package app.windusth.mpvdanmaku.ui.browser.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.windusth.mpvdanmaku.preferences.AppearancePreferences
import app.windusth.mpvdanmaku.preferences.BrowserPreferences
import app.windusth.mpvdanmaku.preferences.preference.collectAsState
import app.windusth.mpvdanmaku.domain.network.NetworkConnection
import app.windusth.mpvdanmaku.domain.network.NetworkFile
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.platform.LocalConfiguration
import org.koin.compose.koinInject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NetworkVideoCard(
  file: NetworkFile,
  connection: NetworkConnection,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  onLongClick: (() -> Unit)? = null,
  isSelected: Boolean = false,
  isGridMode: Boolean = false,
) {
  val appearancePreferences = koinInject<AppearancePreferences>()
  val browserPreferences = koinInject<BrowserPreferences>()
  val unlimitedNameLines by appearancePreferences.unlimitedNameLines.collectAsState()
  val showSizeChip by browserPreferences.showSizeChip.collectAsState()
  val maxLines = if (unlimitedNameLines) Int.MAX_VALUE else 2

  val thumbSizeDp = 64.dp

  Card(
    modifier =
      modifier
        .fillMaxWidth()
        .combinedClickable(
          onClick = onClick,
          onLongClick = onLongClick,
        ),
    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
  ) {
    if (isGridMode) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(
            if (isSelected) {
              MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
            } else {
              Color.Transparent
            },
          )
          .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        val folderGridColumnsPortrait by browserPreferences.folderGridColumnsPortrait.collectAsState()
        val folderGridColumnsLandscape by browserPreferences.folderGridColumnsLandscape.collectAsState()
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val folderGridColumns = if (isLandscape) folderGridColumnsLandscape else folderGridColumnsPortrait
        val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
        val horizontalPadding = 32.dp
        val spacing = 8.dp

        val thumbWidthDp = if (folderGridColumns > 1) {
          val totalSpacing = spacing * (folderGridColumns - 1)
          ((screenWidthDp - horizontalPadding - totalSpacing) / folderGridColumns).coerceAtLeast(120.dp)
        } else {
          160.dp
        }
        val aspect = 16f / 9f
        val thumbHeightDp = thumbWidthDp / aspect
        
        Box(
          modifier = Modifier
            .width(thumbWidthDp)
            .height(thumbHeightDp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(
              onClick = onClick,
              onLongClick = onLongClick,
            ),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            Icons.Filled.PlayArrow,
            contentDescription = "Play",
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.secondary,
          )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
          file.name,
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = maxLines,
          overflow = TextOverflow.Ellipsis,
          textAlign = TextAlign.Center,
        )
      }
    } else {
      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .background(
              if (isSelected) {
                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
              } else {
                Color.Transparent
              },
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        // Square thumbnail matching folder icon size
        Box(
          modifier =
            Modifier
              .size(thumbSizeDp)
              .clip(RoundedCornerShape(12.dp))
              .background(MaterialTheme.colorScheme.surfaceContainerHigh)
              .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
              ),
          contentAlignment = Alignment.Center,
        ) {
          // Play icon overlay
          Icon(
            Icons.Filled.PlayArrow,
            contentDescription = "Play",
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.secondary,
          )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(
          modifier = Modifier.weight(1f),
        ) {
          Text(
            file.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
          )
          Spacer(modifier = Modifier.height(4.dp))
          FlowRow(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
          ) {
            if (showSizeChip && file.size > 0) {
              Text(
                formatFileSize(file.size),
                style = MaterialTheme.typography.labelSmall,
                modifier =
                  Modifier
                    .background(
                      MaterialTheme.colorScheme.surfaceContainerHigh,
                      RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
            if (file.lastModified > 0) {
              Text(
                formatDate(file.lastModified),
                style = MaterialTheme.typography.labelSmall,
                modifier =
                  Modifier
                    .background(
                      MaterialTheme.colorScheme.surfaceContainerHigh,
                      RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
          }
        }
      }
    }
  }
}

private fun formatFileSize(bytes: Long): String {
  return when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
  }
}

private fun formatDate(timestamp: Long): String {
  val date = Date(timestamp)
  val format = SimpleDateFormat("MMM dd", Locale.getDefault())
  return format.format(date)
}

