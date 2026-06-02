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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
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
import androidx.compose.ui.unit.dp
import app.windusth.mpvdanmaku.preferences.AppearancePreferences
import app.windusth.mpvdanmaku.preferences.BrowserPreferences
import app.windusth.mpvdanmaku.preferences.preference.collectAsState
import app.windusth.mpvdanmaku.domain.network.NetworkFile
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.platform.LocalConfiguration
import org.koin.compose.koinInject

@Composable
fun NetworkFolderCard(
  file: NetworkFile,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  onLongClick: (() -> Unit)? = null,
  isSelected: Boolean = false,
  isGridMode: Boolean = false,
) {
  val appearancePreferences = koinInject<AppearancePreferences>()
  val browserPreferences = koinInject<BrowserPreferences>()
  val unlimitedNameLines by appearancePreferences.unlimitedNameLines.collectAsState()
  val maxLines = if (unlimitedNameLines) Int.MAX_VALUE else 2

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
            if (isSelected) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f) else Color.Transparent,
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
            Icons.Filled.Folder,
            contentDescription = "Folder",
            modifier = Modifier.size(56.dp),
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
          textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
      }
    } else {
      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .background(
              if (isSelected) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f) else Color.Transparent,
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(
          modifier =
            Modifier
              .size(64.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(MaterialTheme.colorScheme.surfaceContainerHigh)
              .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
              ),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            Icons.Filled.Folder,
            contentDescription = "Folder",
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
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}
