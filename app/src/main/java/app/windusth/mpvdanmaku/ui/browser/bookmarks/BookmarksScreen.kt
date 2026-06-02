package app.windusth.mpvdanmaku.ui.browser.bookmarks

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.windusth.mpvdanmaku.database.entities.BookmarkEntity
import app.windusth.mpvdanmaku.presentation.Screen
import app.windusth.mpvdanmaku.ui.browser.LocalNavigationBarHeight
import app.windusth.mpvdanmaku.ui.browser.components.BrowserTopBar
import app.windusth.mpvdanmaku.ui.browser.filesystem.FileSystemDirectoryScreen
import app.windusth.mpvdanmaku.ui.browser.networkstreaming.NetworkBrowserScreen
import app.windusth.mpvdanmaku.ui.browser.states.EmptyState
import app.windusth.mpvdanmaku.ui.preferences.PreferencesScreen
import app.windusth.mpvdanmaku.ui.utils.LocalBackStack
import kotlinx.serialization.Serializable

@Serializable
object BookmarksScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val viewModel: BookmarksViewModel = viewModel()
    val bookmarks by viewModel.bookmarks.collectAsState()
    val backstack = LocalBackStack.current

    var renamingBookmark by remember { mutableStateOf<BookmarkEntity?>(null) }

    if (renamingBookmark != null) {
      RenameBookmarkDialog(
        currentName = renamingBookmark!!.name,
        onConfirm = { newName ->
          viewModel.renameBookmark(renamingBookmark!!, newName)
          renamingBookmark = null
        },
        onDismiss = { renamingBookmark = null },
      )
    }

    Scaffold(
      topBar = {
        BrowserTopBar(
          title = "Bookmarks",
          isInSelectionMode = false,
          selectedCount = 0,
          totalCount = bookmarks.size,
          onCancelSelection = { },
          onSettingsClick = {
            backstack.add(PreferencesScreen)
          },
        )
      },
    ) { paddingValues ->
      if (bookmarks.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
          contentAlignment = Alignment.Center,
        ) {
          EmptyState(
            icon = Icons.Filled.Folder,
            title = "No Bookmarks",
            message = "You haven't bookmarked any folders yet.",
          )
        }
      } else {
        val navigationBarHeight = LocalNavigationBarHeight.current
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(
            start = 8.dp,
            end = 8.dp,
            top = paddingValues.calculateTopPadding() + 8.dp,
            bottom = navigationBarHeight + 8.dp,
          ),
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
                      currentPath = bookmark.path,
                    ),
                  )
                } else {
                  backstack.add(FileSystemDirectoryScreen(path = bookmark.path))
                }
              },
              onDeleteClick = { viewModel.deleteBookmark(bookmark) },
              onRenameClick = { renamingBookmark = bookmark },
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
  onRenameClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier
      .fillMaxWidth()
      .combinedClickable(
        onClick = onClick,
        onLongClick = onRenameClick,
      ),
    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier
          .size(64.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          if (bookmark.type == "NETWORK") Icons.Filled.Language else Icons.Filled.Folder,
          contentDescription = null,
          modifier = Modifier.size(48.dp),
          tint = MaterialTheme.colorScheme.secondary,
        )
      }
      Spacer(modifier = Modifier.width(16.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = bookmark.name,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = bookmark.path,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      IconButton(onClick = onDeleteClick) {
        Icon(
          Icons.Filled.Delete,
          contentDescription = "Delete Bookmark",
          tint = MaterialTheme.colorScheme.error,
        )
      }
    }
  }
}

@Composable
private fun RenameBookmarkDialog(
  currentName: String,
  onConfirm: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var name by remember { mutableStateOf(currentName) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Rename Bookmark") },
    text = {
      OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
    },
    confirmButton = {
      TextButton(
        onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
        enabled = name.isNotBlank(),
      ) {
        Text("OK")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    },
  )
}
