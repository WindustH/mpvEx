package app.windusth.mpvdanmuku.ui.browser.bookmarks

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.windusth.mpvdanmuku.database.entities.BookmarkEntity
import app.windusth.mpvdanmuku.presentation.Screen
import app.windusth.mpvdanmuku.ui.browser.components.BrowserTopBar
import app.windusth.mpvdanmuku.ui.browser.filesystem.FileSystemBrowserRootScreen
import app.windusth.mpvdanmuku.ui.browser.filesystem.FileSystemBrowserScreen
import app.windusth.mpvdanmuku.ui.browser.filesystem.FileSystemDirectoryScreen
import app.windusth.mpvdanmuku.ui.browser.networkstreaming.NetworkBrowserScreen
import app.windusth.mpvdanmuku.ui.browser.states.EmptyState
import app.windusth.mpvdanmuku.ui.utils.LocalBackStack
import kotlinx.serialization.Serializable

@Serializable
object BookmarksScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val viewModel: BookmarksViewModel = viewModel()
    val bookmarks by viewModel.bookmarks.collectAsState()
    val backstack = LocalBackStack.current

    Scaffold(
      topBar = {
        BrowserTopBar(
          title = "Bookmarks",
          isInSelectionMode = false,
          selectedCount = 0,
          totalCount = bookmarks.size,
          onCancelSelection = { },
          onSettingsClick = {
            backstack.add(app.windusth.mpvdanmuku.ui.preferences.PreferencesScreen)
          }
        )
      }
    ) { paddingValues ->
      if (bookmarks.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
          contentAlignment = Alignment.Center
        ) {
          EmptyState(
            icon = Icons.Filled.Folder,
            title = "No Bookmarks",
            message = "You haven't bookmarked any folders yet."
          )
        }
      } else {
        val navigationBarHeight = app.windusth.mpvdanmuku.ui.browser.LocalNavigationBarHeight.current
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(
            start = 8.dp,
            end = 8.dp,
            top = paddingValues.calculateTopPadding() + 8.dp,
            bottom = navigationBarHeight + 8.dp
          )
        ) {
          items(bookmarks, key = { it.id }) { bookmark ->
            BookmarkCard(
              bookmark = bookmark,
              onClick = {
                if (bookmark.type == "NETWORK") {
                  backstack.add(
                    NetworkBrowserScreen(
                      connectionId = bookmark.connectionId ?: 0L,
                      connectionName = bookmark.name,
                      currentPath = bookmark.path
                    )
                  )
                } else {
                  // Navigate to Local File Browser
                  backstack.add(FileSystemDirectoryScreen(path = bookmark.path))
                }
              },
              onDeleteClick = { viewModel.deleteBookmark(bookmark) }
            )
          }
        }
      }
    }
  }
}

@Composable
private fun BookmarkCard(
  bookmark: BookmarkEntity,
  onClick: () -> Unit,
  onDeleteClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier
      .fillMaxWidth()
      .combinedClickable(onClick = onClick),
    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(56.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          if (bookmark.type == "NETWORK") Icons.Filled.Language else Icons.Filled.Folder,
          contentDescription = null,
          modifier = Modifier.size(32.dp),
          tint = MaterialTheme.colorScheme.secondary
        )
      }
      Spacer(modifier = Modifier.width(16.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = bookmark.name,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = bookmark.path,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
      IconButton(onClick = onDeleteClick) {
        Icon(
          Icons.Filled.Delete,
          contentDescription = "Delete Bookmark",
          tint = MaterialTheme.colorScheme.error
        )
      }
    }
  }
}
