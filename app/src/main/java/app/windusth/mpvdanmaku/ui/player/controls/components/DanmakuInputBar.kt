package app.windusth.mpvdanmaku.ui.player.controls.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
fun DanmakuInputBar(
  isSending: Boolean,
  errorMessage: String?,
  onSend: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  var text by remember { mutableStateOf("") }

  var wasSending by remember { mutableStateOf(false) }
  LaunchedEffect(isSending, errorMessage) {
    if (wasSending && !isSending && errorMessage == null) {
      text = ""
    }
    wasSending = isSending
  }

  Row(
    modifier = modifier
      .fillMaxWidth()
      .background(
        color = Color.Black.copy(alpha = 0.5f),
        shape = RoundedCornerShape(24.dp),
      )
      .padding(horizontal = 12.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    TextField(
      value = text,
      onValueChange = { if (it.length <= 100) text = it },
      placeholder = {
        Text(
          "发送弹幕",
          color = Color.White.copy(alpha = 0.5f),
          style = MaterialTheme.typography.bodyMedium,
        )
      },
      singleLine = true,
      enabled = !isSending,
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
      keyboardActions = KeyboardActions(
        onSend = {
          if (text.isNotBlank()) {
            onSend(text.trim())
          }
        },
      ),
      modifier = Modifier.weight(1f),
      colors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        cursorColor = Color.White,
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        disabledTextColor = Color.White.copy(alpha = 0.38f),
      ),
      textStyle = MaterialTheme.typography.bodyMedium,
    )

    IconButton(
      onClick = {
        if (text.isNotBlank()) {
          onSend(text.trim())
        }
      },
      enabled = text.isNotBlank() && !isSending,
      colors = IconButtonDefaults.iconButtonColors(
        contentColor = Color.White,
        disabledContentColor = Color.White.copy(alpha = 0.38f),
      ),
    ) {
      if (isSending) {
        CircularProgressIndicator(
          modifier = Modifier.size(20.dp),
          strokeWidth = 2.dp,
          color = Color.White,
        )
      } else {
        Icon(
          Icons.AutoMirrored.Filled.Send,
          contentDescription = "Send",
          modifier = Modifier.size(20.dp),
        )
      }
    }
  }
}
