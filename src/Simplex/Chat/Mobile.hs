{-# LANGUAGE DeriveGeneric #-}
{-# LANGUAGE DuplicateRecordFields #-}
{-# LANGUAGE LambdaCase #-}
{-# LANGUAGE NamedFieldPuns #-}
{-# LANGUAGE OverloadedStrings #-}

module Simplex.Chat.Mobile where

import Control.Concurrent.STM
import Control.Exception (catch)
import Control.Monad.Except
import Control.Monad.Reader
import Data.Aeson (ToJSON (..))
import qualified Data.Aeson as J
import Data.Bifunctor (first)
import qualified Data.ByteString.Base64.URL as U
import qualified Data.ByteString.Char8 as B
import qualified Data.ByteString.Lazy.Char8 as LB
import Data.Functor (($>))
import Data.List (find)
import qualified Data.List.NonEmpty as L
import Data.Maybe (fromMaybe)
import qualified Data.Text as T
import Data.Text.Encoding (encodeUtf8)
import Data.Word (Word8)
import Database.SQLite.Simple (SQLError (..))
import qualified Database.SQLite.Simple as DB
import Foreign.C.String
import Foreign.C.Types (CInt (..))
import Foreign.Ptr
import Foreign.StablePtr
import Foreign.Storable (poke)
import GHC.Generics (Generic)
import Simplex.Chat
import Simplex.Chat.Controller
import Simplex.Chat.Markdown (ParsedMarkdown (..), parseMaybeMarkdownList)
import Simplex.Chat.Mobile.WebRTC
import Simplex.Chat.Options
import Simplex.Chat.Store
import Simplex.Chat.Types
import Simplex.Messaging.Agent.Env.SQLite (createAgentStore)
import Simplex.Messaging.Agent.Store.SQLite (MigrationConfirmation (..), MigrationError)
import Simplex.Messaging.Client (defaultNetworkConfig)
import qualified Simplex.Messaging.Crypto as C
import Simplex.Messaging.Encoding.String
import Simplex.Messaging.Parsers (dropPrefix, sumTypeJSON)
import Simplex.Messaging.Protocol (AProtoServerWithAuth (..), AProtocolType (..), BasicAuth (..), CorrId (..), ProtoServerWithAuth (..), ProtocolServer (..))
import Simplex.Messaging.Util (catchAll, liftEitherWith, safeDecodeUtf8)
import System.Timeout (timeout)

foreign export ccall "chat_migrate_init" cChatMigrateInit :: CString -> CString -> CString -> Ptr (StablePtr ChatController) -> IO CJSONString

foreign export ccall "chat_send_cmd" cChatSendCmd :: StablePtr ChatController -> CString -> IO CJSONString

foreign export ccall "chat_recv_msg" cChatRecvMsg :: StablePtr ChatController -> IO CJSONString

foreign export ccall "chat_recv_msg_wait" cChatRecvMsgWait :: StablePtr ChatController -> CInt -> IO CJSONString

foreign export ccall "chat_parse_markdown" cChatParseMarkdown :: CString -> IO CJSONString

foreign export ccall "chat_parse_server" cChatParseServer :: CString -> IO CJSONString

foreign export ccall "chat_password_hash" cChatPasswordHash :: CString -> CString -> IO CString

foreign export ccall "chat_encrypt_media" cChatEncryptMedia :: CString -> Ptr Word8 -> CInt -> IO CString

foreign export ccall "chat_decrypt_media" cChatDecryptMedia :: CString -> Ptr Word8 -> CInt -> IO CString

-- | check / migrate database and initialize chat controller on success
cChatMigrateInit :: CString -> CString -> CString -> Ptr (StablePtr ChatController) -> IO CJSONString
cChatMigrateInit fp key conf ctrl = do
  dbPath <- peekCAString fp
  dbKey <- peekCAString key
  confirm <- peekCAString conf
  r <-
    chatMigrateInit dbPath dbKey confirm >>= \case
      Right cc -> (newStablePtr cc >>= poke ctrl) $> DBMOk
      Left e -> pure e
  newCAString . LB.unpack $ J.encode r

-- | send command to chat (same syntax as in terminal for now)
cChatSendCmd :: StablePtr ChatController -> CString -> IO CJSONString
cChatSendCmd cPtr cCmd = do
  c <- deRefStablePtr cPtr
  cmd <- peekCAString cCmd
  newCAString =<< chatSendCmd c cmd

-- | receive message from chat (blocking)
cChatRecvMsg :: StablePtr ChatController -> IO CJSONString
cChatRecvMsg cc = deRefStablePtr cc >>= chatRecvMsg >>= newCAString

-- |  receive message from chat (blocking up to `t` microseconds (1/10^6 sec), returns empty string if times out)
cChatRecvMsgWait :: StablePtr ChatController -> CInt -> IO CJSONString
cChatRecvMsgWait cc t = deRefStablePtr cc >>= (`chatRecvMsgWait` fromIntegral t) >>= newCAString

-- | parse markdown - returns ParsedMarkdown type JSON
cChatParseMarkdown :: CString -> IO CJSONString
cChatParseMarkdown s = newCAString . chatParseMarkdown =<< peekCAString s

-- | parse server address - returns ParsedServerAddress JSON
cChatParseServer :: CString -> IO CJSONString
cChatParseServer s = newCAString . chatParseServer =<< peekCAString s

cChatPasswordHash :: CString -> CString -> IO CString
cChatPasswordHash cPwd cSalt = do
  pwd <- peekCAString cPwd
  salt <- peekCAString cSalt
  newCAString $ chatPasswordHash pwd salt

mobileChatOpts :: String -> String -> ChatOpts
mobileChatOpts dbFilePrefix dbKey =
  ChatOpts
    { coreOptions =
        CoreChatOpts
          { dbFilePrefix,
            dbKey,
            smpServers = [],
            xftpServers = [],
            networkConfig = defaultNetworkConfig,
            logLevel = CLLImportant,
            logConnections = False,
            logServerHosts = True,
            logAgent = Nothing,
            logFile = Nothing,
            tbqSize = 1024
          },
      chatCmd = "",
      chatCmdDelay = 3,
      chatServerPort = Nothing,
      optFilesFolder = Nothing,
      allowInstantFiles = True,
      maintenance = True
    }

defaultMobileConfig :: ChatConfig
defaultMobileConfig =
  defaultChatConfig
    { confirmMigrations = MCYesUp,
      logLevel = CLLError
    }

type CJSONString = CString

getActiveUser_ :: SQLiteStore -> IO (Maybe User)
getActiveUser_ st = find activeUser <$> withTransaction st getUsers

data DBMigrationResult
  = DBMOk
  | DBMInvalidConfirmation
  | DBMErrorNotADatabase {dbFile :: String}
  | DBMErrorMigration {dbFile :: String, migrationError :: MigrationError}
  | DBMErrorSQL {dbFile :: String, migrationSQLError :: String}
  deriving (Show, Generic)

instance ToJSON DBMigrationResult where
  toJSON = J.genericToJSON . sumTypeJSON $ dropPrefix "DBM"
  toEncoding = J.genericToEncoding . sumTypeJSON $ dropPrefix "DBM"

chatMigrateInit :: String -> String -> String -> IO (Either DBMigrationResult ChatController)
chatMigrateInit dbFilePrefix dbKey confirm = runExceptT $ do
  confirmMigrations <- liftEitherWith (const DBMInvalidConfirmation) $ strDecode $ B.pack confirm
  chatStore <- migrate createChatStore (chatStoreFile dbFilePrefix) confirmMigrations
  agentStore <- migrate createAgentStore (agentStoreFile dbFilePrefix) confirmMigrations
  liftIO $ initialize chatStore ChatDatabase {chatStore, agentStore}
  where
    initialize st db = do
      user_ <- getActiveUser_ st
      newChatController db user_ defaultMobileConfig (mobileChatOpts dbFilePrefix dbKey) Nothing
    migrate createStore dbFile confirmMigrations =
      ExceptT $
        (first (DBMErrorMigration dbFile) <$> createStore dbFile dbKey confirmMigrations)
          `catch` (pure . checkDBError)
            `catchAll` (pure . dbError)
      where
        checkDBError e = case sqlError e of
          DB.ErrorNotADatabase -> Left $ DBMErrorNotADatabase dbFile
          _ -> dbError e
        dbError e = Left . DBMErrorSQL dbFile $ show e

chatSendCmd :: ChatController -> String -> IO JSONString
chatSendCmd cc s = LB.unpack . J.encode . APIResponse Nothing <$> runReaderT (execChatCommand $ B.pack s) cc

chatRecvMsg :: ChatController -> IO JSONString
chatRecvMsg ChatController {outputQ} = json <$> atomically (readTBQueue outputQ)
  where
    json (corr, resp) = LB.unpack $ J.encode APIResponse {corr, resp}

chatRecvMsgWait :: ChatController -> Int -> IO JSONString
chatRecvMsgWait cc time = fromMaybe "" <$> timeout time (chatRecvMsg cc)

chatParseMarkdown :: String -> JSONString
chatParseMarkdown = LB.unpack . J.encode . ParsedMarkdown . parseMaybeMarkdownList . safeDecodeUtf8 . B.pack

chatParseServer :: String -> JSONString
chatParseServer = LB.unpack . J.encode . toServerAddress . strDecode . B.pack
  where
    toServerAddress :: Either String AProtoServerWithAuth -> ParsedServerAddress
    toServerAddress = \case
      Right (AProtoServerWithAuth protocol (ProtoServerWithAuth ProtocolServer {host, port, keyHash = C.KeyHash kh} auth)) ->
        let basicAuth = maybe "" (\(BasicAuth a) -> enc a) auth
         in ParsedServerAddress (Just ServerAddress {serverProtocol = AProtocolType protocol, hostnames = L.map enc host, port, keyHash = enc kh, basicAuth}) ""
      Left e -> ParsedServerAddress Nothing e
    enc :: StrEncoding a => a -> String
    enc = B.unpack . strEncode

chatPasswordHash :: String -> String -> String
chatPasswordHash pwd salt = either (const "") passwordHash salt'
  where
    salt' = U.decode $ B.pack salt
    passwordHash = B.unpack . U.encode . C.sha512Hash . (encodeUtf8 (T.pack pwd) <>)

data APIResponse = APIResponse {corr :: Maybe CorrId, resp :: ChatResponse}
  deriving (Generic)

instance ToJSON APIResponse where
  toJSON = J.genericToJSON J.defaultOptions {J.omitNothingFields = True}
  toEncoding = J.genericToEncoding J.defaultOptions {J.omitNothingFields = True}
