package chat.simplex.app.views.usersettings

import SectionCustomFooter
import SectionDivider
import SectionItemView
import SectionItemWithValue
import SectionSpacer
import SectionTextFooter
import SectionView
import SectionViewSelectable
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import chat.simplex.app.R
import chat.simplex.app.model.*
import chat.simplex.app.ui.theme.*
import chat.simplex.app.views.helpers.*

@Composable
fun NetworkAndServersView(
  chatModel: ChatModel,
  showModal: (@Composable (ChatModel) -> Unit) -> (() -> Unit),
  showSettingsModal: (@Composable (ChatModel) -> Unit) -> (() -> Unit),
  showCustomModal: (@Composable (ChatModel, () -> Unit) -> Unit) -> (() -> Unit),
) {
  // It's not a state, just a one-time value. Shouldn't be used in any state-related situations
  val netCfg = remember { chatModel.controller.getNetCfg() }
  val networkUseSocksProxy: MutableState<Boolean> = remember { mutableStateOf(netCfg.useSocksProxy) }
  val developerTools = chatModel.controller.appPrefs.developerTools.get()
  val onionHosts = remember { mutableStateOf(netCfg.onionHosts) }
  val sessionMode = remember { mutableStateOf(netCfg.sessionMode) }

  LaunchedEffect(Unit) {
    chatModel.userSMPServersUnsaved.value = null
  }

  NetworkAndServersLayout(
    developerTools = developerTools,
    networkUseSocksProxy = networkUseSocksProxy,
    onionHosts = onionHosts,
    sessionMode = sessionMode,
    proxyPort = remember { derivedStateOf { chatModel.controller.appPrefs.networkProxyHostPort.state.value?.split(":")?.lastOrNull()?.toIntOrNull() ?: 9050 } },
    showModal = showModal,
    showSettingsModal = showSettingsModal,
    showCustomModal = showCustomModal,
    toggleSocksProxy = { enable ->
      if (enable) {
        AlertManager.shared.showAlertMsg(
          title = generalGetString(R.string.network_enable_socks),
          text = generalGetString(R.string.network_enable_socks_info),
          confirmText = generalGetString(R.string.confirm_verb),
          onConfirm = {
            withApi {
              chatModel.controller.apiSetNetworkConfig(NetCfg.proxyDefaults)
              chatModel.controller.setNetCfg(NetCfg.proxyDefaults)
              networkUseSocksProxy.value = true
              onionHosts.value = NetCfg.proxyDefaults.onionHosts
            }
          }
        )
      } else {
        AlertManager.shared.showAlertMsg(
          title = generalGetString(R.string.network_disable_socks),
          text = generalGetString(R.string.network_disable_socks_info),
          confirmText = generalGetString(R.string.confirm_verb),
          onConfirm = {
            withApi {
              chatModel.controller.apiSetNetworkConfig(NetCfg.defaults)
              chatModel.controller.setNetCfg(NetCfg.defaults)
              networkUseSocksProxy.value = false
              onionHosts.value = NetCfg.defaults.onionHosts
            }
          }
        )
      }
    },
    useOnion = {
      if (onionHosts.value == it) return@NetworkAndServersLayout
      val prevValue = onionHosts.value
      onionHosts.value = it
      val startsWith = when (it) {
        OnionHosts.NEVER -> generalGetString(R.string.network_use_onion_hosts_no_desc_in_alert)
        OnionHosts.PREFER -> generalGetString(R.string.network_use_onion_hosts_prefer_desc_in_alert)
        OnionHosts.REQUIRED -> generalGetString(R.string.network_use_onion_hosts_required_desc_in_alert)
      }
      showUpdateNetworkSettingsDialog(
        title = generalGetString(R.string.update_onion_hosts_settings_question),
        startsWith,
        onDismiss = {
          onionHosts.value = prevValue
        }
      ) {
        withApi {
          val newCfg = chatModel.controller.getNetCfg().withOnionHosts(it)
          val res = chatModel.controller.apiSetNetworkConfig(newCfg)
          if (res) {
            chatModel.controller.setNetCfg(newCfg)
            onionHosts.value = it
          } else {
            onionHosts.value = prevValue
          }
        }
      }
    },
    updateSessionMode = {
      if (sessionMode.value == it) return@NetworkAndServersLayout
      val prevValue = sessionMode.value
      sessionMode.value = it
      val startsWith = when (it) {
        TransportSessionMode.User -> generalGetString(R.string.network_session_mode_user_description)
        TransportSessionMode.Entity -> generalGetString(R.string.network_session_mode_entity_description)
      }
      showUpdateNetworkSettingsDialog(
        title = generalGetString(R.string.update_network_session_mode_question),
        startsWith,
        onDismiss = { sessionMode.value = prevValue }
      ) {
        withApi {
          val newCfg = chatModel.controller.getNetCfg().copy(sessionMode = it)
          val res = chatModel.controller.apiSetNetworkConfig(newCfg)
          if (res) {
            chatModel.controller.setNetCfg(newCfg)
            sessionMode.value = it
          } else {
            sessionMode.value = prevValue
          }
        }
      }
    }
  )
}

@Composable fun NetworkAndServersLayout(
  developerTools: Boolean,
  networkUseSocksProxy: MutableState<Boolean>,
  onionHosts: MutableState<OnionHosts>,
  sessionMode: MutableState<TransportSessionMode>,
  proxyPort: State<Int>,
  showModal: (@Composable (ChatModel) -> Unit) -> (() -> Unit),
  showSettingsModal: (@Composable (ChatModel) -> Unit) -> (() -> Unit),
  showCustomModal: (@Composable (ChatModel, () -> Unit) -> Unit) -> (() -> Unit),
  toggleSocksProxy: (Boolean) -> Unit,
  useOnion: (OnionHosts) -> Unit,
  updateSessionMode: (TransportSessionMode) -> Unit,
) {
  Column(
    Modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.Start,
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    AppBarTitle(stringResource(R.string.network_and_servers))
    SectionView(generalGetString(R.string.settings_section_title_messages)) {
      SettingsActionItem(Icons.Outlined.Dns, stringResource(R.string.smp_servers), showCustomModal { m, close -> ProtocolServersView(m, ServerProtocol.SMP, close) })
      SectionDivider()

      SettingsActionItem(Icons.Outlined.Dns, stringResource(R.string.xftp_servers), showCustomModal { m, close -> ProtocolServersView(m, ServerProtocol.XFTP, close) })
      SectionDivider()

      SectionItemView {
        UseSocksProxySwitch(networkUseSocksProxy, proxyPort, toggleSocksProxy, showSettingsModal)
      }
      SectionDivider()
      UseOnionHosts(onionHosts, networkUseSocksProxy, showSettingsModal, useOnion)
      SectionDivider()
      if (developerTools) {
        SessionModePicker(sessionMode, showSettingsModal, updateSessionMode)
        SectionDivider()
      }
      SettingsActionItem(Icons.Outlined.Cable, stringResource(R.string.network_settings), showSettingsModal { AdvancedNetworkSettingsView(it) })
    }
    if (networkUseSocksProxy.value) {
      SectionCustomFooter { Text(annotatedStringResource(R.string.disable_onion_hosts_when_not_supported)) }
    }
    Spacer(Modifier.height(16.dp))
    SectionView(generalGetString(R.string.settings_section_title_calls)) {
      SettingsActionItem(Icons.Outlined.ElectricalServices, stringResource(R.string.webrtc_ice_servers), showModal { RTCServersView(it) })
    }
  }
}

@Composable
fun UseSocksProxySwitch(
  networkUseSocksProxy: MutableState<Boolean>,
  proxyPort: State<Int>,
  toggleSocksProxy: (Boolean) -> Unit,
  showSettingsModal: (@Composable (ChatModel) -> Unit) -> (() -> Unit)
) {
  Row(
    Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Row(
      Modifier.weight(1f),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Icon(
        Icons.Outlined.SettingsEthernet,
        stringResource(R.string.network_socks_toggle),
        tint = HighOrLowlight
      )
      if (networkUseSocksProxy.value) {
        Row {
          Text(generalGetString(R.string.network_socks_toggle_use_socks_proxy) + " (")
          Text(
            generalGetString(R.string.network_proxy_port).format(proxyPort.value),
            Modifier.clickable { showSettingsModal { SockProxySettings(it) }() },
            color = MaterialTheme.colors.primary
          )
          Text(")")
        }
      } else {
        Text(stringResource(R.string.network_socks_toggle))
      }
    }
    Switch(
      checked = networkUseSocksProxy.value,
      onCheckedChange = toggleSocksProxy,
      colors = SwitchDefaults.colors(
        checkedThumbColor = MaterialTheme.colors.primary,
        uncheckedThumbColor = HighOrLowlight
      ),
    )
  }
}

@Composable
fun SockProxySettings(m: ChatModel) {
  Column(
    Modifier
      .fillMaxWidth()
      .verticalScroll(rememberScrollState())
      .padding(bottom = DEFAULT_BOTTOM_PADDING),
  ) {
    val defaultHostPort = remember { "localhost:9050" }
    AppBarTitle(generalGetString(R.string.network_socks_proxy_settings))
    val hostPort by remember { m.controller.appPrefs.networkProxyHostPort.state }
    val hostUnsaved = rememberSaveable(stateSaver = TextFieldValue.Saver) {
      mutableStateOf(TextFieldValue(hostPort?.split(":")?.firstOrNull() ?: "localhost"))
    }
    val portUnsaved = rememberSaveable(stateSaver = TextFieldValue.Saver) {
      mutableStateOf(TextFieldValue(hostPort?.split(":")?.lastOrNull() ?: "9050"))
    }
    val save = {
      withBGApi {
        m.controller.appPrefs.networkProxyHostPort.set(hostUnsaved.value.text + ":" + portUnsaved.value.text)
        m.controller.apiSetNetworkConfig(m.controller.getNetCfg())
      }
    }
    SectionView {
      SectionItemView {
        ResetToDefaultsButton({
          showUpdateNetworkSettingsDialog {
            m.controller.appPrefs.networkProxyHostPort.set(defaultHostPort)
            val newHost = defaultHostPort.split(":").first()
            val newPort = defaultHostPort.split(":").last()
            hostUnsaved.value = hostUnsaved.value.copy(newHost, TextRange(newHost.length))
            portUnsaved.value = portUnsaved.value.copy(newPort, TextRange(newPort.length))
            save()
          }
        }, disabled = hostPort == defaultHostPort)
      }
      SectionDivider()
      SectionItemView {
        DefaultConfigurableTextField(
          hostUnsaved,
          stringResource(R.string.host_verb),
          modifier = Modifier,
          isValid = ::validHost,
          keyboardActions = KeyboardActions(onNext = { defaultKeyboardAction(ImeAction.Next) }),
          keyboardType = KeyboardType.Text,
        )
      }
      SectionDivider()
      SectionItemView {
        DefaultConfigurableTextField(
          portUnsaved,
          stringResource(R.string.port_verb),
          modifier = Modifier,
          isValid = ::validPort,
          keyboardActions = KeyboardActions(onDone = { defaultKeyboardAction(ImeAction.Done); save() }),
          keyboardType = KeyboardType.Number,
        )
      }
    }
    SectionCustomFooter {
      NetworkSectionFooter(
        revert = {
          val prevHost = m.controller.appPrefs.networkProxyHostPort.get()?.split(":")?.firstOrNull() ?: "localhost"
          val prevPort = m.controller.appPrefs.networkProxyHostPort.get()?.split(":")?.lastOrNull() ?: "9050"
          hostUnsaved.value = hostUnsaved.value.copy(prevHost, TextRange(prevHost.length))
          portUnsaved.value = portUnsaved.value.copy(prevPort, TextRange(prevPort.length))
        },
        save = { showUpdateNetworkSettingsDialog { save() } },
        revertDisabled = hostPort == (hostUnsaved.value.text + ":" + portUnsaved.value.text),
        saveDisabled = hostPort == (hostUnsaved.value.text + ":" + portUnsaved.value.text) ||
            remember { derivedStateOf { !validHost(hostUnsaved.value.text) } }.value ||
            remember { derivedStateOf { !validPort(portUnsaved.value.text) } }.value
      )
    }
  }
}

@Composable
private fun UseOnionHosts(
  onionHosts: MutableState<OnionHosts>,
  enabled: State<Boolean>,
  showModal: (@Composable (ChatModel) -> Unit) -> (() -> Unit),
  useOnion: (OnionHosts) -> Unit,
) {
  val values = remember {
    OnionHosts.values().map {
      when (it) {
        OnionHosts.NEVER -> ValueTitleDesc(OnionHosts.NEVER, generalGetString(R.string.network_use_onion_hosts_no), generalGetString(R.string.network_use_onion_hosts_no_desc))
        OnionHosts.PREFER -> ValueTitleDesc(OnionHosts.PREFER, generalGetString(R.string.network_use_onion_hosts_prefer), generalGetString(R.string.network_use_onion_hosts_prefer_desc))
        OnionHosts.REQUIRED -> ValueTitleDesc(OnionHosts.REQUIRED, generalGetString(R.string.network_use_onion_hosts_required), generalGetString(R.string.network_use_onion_hosts_required_desc))
      }
    }
  }
  val onSelected = showModal {
    Column(
      Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.Start,
    ) {
      AppBarTitle(stringResource(R.string.network_use_onion_hosts))
      SectionViewSelectable(null, onionHosts, values, useOnion)
    }
  }

  SectionItemWithValue(
    generalGetString(R.string.network_use_onion_hosts),
    onionHosts,
    values,
    icon = Icons.Outlined.Security,
    enabled = enabled,
    onSelected = onSelected
  )
}

@Composable
private fun SessionModePicker(
  sessionMode: MutableState<TransportSessionMode>,
  showModal: (@Composable (ChatModel) -> Unit) -> (() -> Unit),
  updateSessionMode: (TransportSessionMode) -> Unit,
) {
  val values = remember {
    TransportSessionMode.values().map {
      when (it) {
        TransportSessionMode.User -> ValueTitleDesc(TransportSessionMode.User, generalGetString(R.string.network_session_mode_user), generalGetString(R.string.network_session_mode_user_description))
        TransportSessionMode.Entity -> ValueTitleDesc(TransportSessionMode.Entity, generalGetString(R.string.network_session_mode_entity), generalGetString(R.string.network_session_mode_entity_description))
      }
    }
  }

  SectionItemWithValue(
    generalGetString(R.string.network_session_mode_transport_isolation),
    sessionMode,
    values,
    icon = Icons.Outlined.SafetyDivider,
    onSelected = showModal {
      Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
      ) {
        AppBarTitle(stringResource(R.string.network_session_mode_transport_isolation))
        SectionViewSelectable(null, sessionMode, values, updateSessionMode)
      }
    }
  )
}

@Composable
private fun NetworkSectionFooter(revert: () -> Unit, save: () -> Unit, revertDisabled: Boolean, saveDisabled: Boolean) {
  Row(
    Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    FooterButton(Icons.Outlined.Replay, stringResource(R.string.network_options_revert), revert, revertDisabled)
    FooterButton(Icons.Outlined.Check, stringResource(R.string.network_options_save), save, saveDisabled)
  }
}

// https://stackoverflow.com/a/106223
private fun validHost(s: String): Boolean {
  val validIp = Regex("^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])[.]){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$")
  val validHostname = Regex("^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9])[.])*([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9-]*[A-Za-z0-9])$");
  return s.matches(validIp) || s.matches(validHostname)
}

// https://ihateregex.io/expr/port/
private fun validPort(s: String): Boolean {
  val validPort = Regex("^(6553[0-5])|(655[0-2][0-9])|(65[0-4][0-9]{2})|(6[0-4][0-9]{3})|([1-5][0-9]{4})|([0-5]{0,5})|([0-9]{1,4})$")
  return s.isNotBlank() && s.matches(validPort)
}

private fun showUpdateNetworkSettingsDialog(
  title: String,
  startsWith: String = "",
  message: String = generalGetString(R.string.updating_settings_will_reconnect_client_to_all_servers),
  onDismiss: () -> Unit,
  onConfirm: () -> Unit
) {
  AlertManager.shared.showAlertDialog(
    title = title,
    text = startsWith + "\n\n" + message,
    confirmText = generalGetString(R.string.update_network_settings_confirmation),
    onDismiss = onDismiss,
    onConfirm = onConfirm,
    onDismissRequest = onDismiss
  )
}

@Preview(showBackground = true)
@Composable
fun PreviewNetworkAndServersLayout() {
  SimpleXTheme {
    NetworkAndServersLayout(
      developerTools = true,
      networkUseSocksProxy = remember { mutableStateOf(true) },
      proxyPort = remember { mutableStateOf(9050) },
      showModal = { {} },
      showSettingsModal = { {} },
      showCustomModal = { {} },
      toggleSocksProxy = {},
      onionHosts = remember { mutableStateOf(OnionHosts.PREFER) },
      sessionMode = remember { mutableStateOf(TransportSessionMode.User) },
      useOnion = {},
      updateSessionMode = {},
    )
  }
}
