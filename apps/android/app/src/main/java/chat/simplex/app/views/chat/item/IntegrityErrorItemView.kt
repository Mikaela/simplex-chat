package chat.simplex.app.views.chat.item

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.app.R
import chat.simplex.app.model.ChatItem
import chat.simplex.app.model.MsgErrorType
import chat.simplex.app.ui.theme.SimpleXTheme
import chat.simplex.app.views.helpers.AlertManager
import chat.simplex.app.views.helpers.generalGetString

@Composable
fun IntegrityErrorItemView(msgError: MsgErrorType, ci: ChatItem, timedMessagesTTL: Int?, showMember: Boolean = false) {
  CIMsgError(ci, timedMessagesTTL, showMember) {
    when (msgError) {
      is MsgErrorType.MsgSkipped ->
        AlertManager.shared.showAlertMsg(
          title = generalGetString(R.string.alert_title_skipped_messages),
          text = generalGetString(R.string.alert_text_skipped_messages_it_can_happen_when)
        )
      is MsgErrorType.MsgBadHash ->
        AlertManager.shared.showAlertMsg(
          title = generalGetString(R.string.alert_title_msg_bad_hash),
          text = generalGetString(R.string.alert_text_msg_bad_hash) + "\n" +
              generalGetString(R.string.alert_text_fragment_encryption_out_of_sync_old_database) + "\n" +
              generalGetString(R.string.alert_text_fragment_please_report_to_developers)
        )
      is MsgErrorType.MsgBadId, is MsgErrorType.MsgDuplicate ->
        AlertManager.shared.showAlertMsg(
          title = generalGetString(R.string.alert_title_msg_bad_id),
          text = generalGetString(R.string.alert_text_msg_bad_id) + "\n" +
              generalGetString(R.string.alert_text_fragment_please_report_to_developers)
        )
    }
  }
}

@Composable
fun CIMsgError(ci: ChatItem, timedMessagesTTL: Int?, showMember: Boolean = false, onClick: () -> Unit) {
  Surface(
    Modifier.clickable(onClick = onClick),
    shape = RoundedCornerShape(18.dp),
    color = ReceivedColorLight,
  ) {
    Row(
      Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
      verticalAlignment = Alignment.Bottom
    ) {
      Text(
        buildAnnotatedString {
          appendSender(this, if (showMember) ci.memberDisplayName else null, true)
          withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = Color.Red)) { append(ci.content.text) }
        },
        style = MaterialTheme.typography.body1.copy(lineHeight = 22.sp),
        modifier = Modifier.padding(end = 8.dp)
      )
      CIMetaView(ci, timedMessagesTTL)
    }
  }
}

@Preview(showBackground = true)
@Preview(
  uiMode = Configuration.UI_MODE_NIGHT_YES,
  name = "Dark Mode"
)
@Composable
fun IntegrityErrorItemViewPreview() {
  SimpleXTheme {
    IntegrityErrorItemView(
      MsgErrorType.MsgBadHash(),
      ChatItem.getDeletedContentSampleData(),
      null
    )
  }
}
