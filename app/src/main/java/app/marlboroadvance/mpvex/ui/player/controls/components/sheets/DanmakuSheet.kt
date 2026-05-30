package app.marlboroadvance.mpvex.ui.player.controls.components.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.repository.danmaku.DanmakuAnime
import app.marlboroadvance.mpvex.repository.danmaku.DanmakuEpisode
import app.marlboroadvance.mpvex.presentation.components.PlayerSheet
import app.marlboroadvance.mpvex.ui.player.DanmakuUiState
import app.marlboroadvance.mpvex.ui.theme.spacing

@Composable
fun DanmakuSheet(
  state: DanmakuUiState,
  onQueryChange: (String) -> Unit,
  onSearch: () -> Unit,
  onSelectAnime: (DanmakuAnime) -> Unit,
  onBackToResults: () -> Unit,
  onLoadEpisode: (DanmakuEpisode) -> Unit,
  onToggle: () -> Unit,
  onClear: () -> Unit,
  onDismissRequest: () -> Unit,
) {
  PlayerSheet(onDismissRequest) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(MaterialTheme.spacing.medium),
      verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Danmaku",
          style = MaterialTheme.typography.headlineMedium,
        )
        IconButton(onClick = onDismissRequest) {
          Icon(Icons.Default.Close, contentDescription = null)
        }
      }

      if (state.loadedLabel != null) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = state.loadedLabel,
              style = MaterialTheme.typography.titleSmall,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = "${state.comments.size} comments",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.outline,
            )
          }
          TextButton(onClick = onToggle, enabled = state.comments.isNotEmpty()) {
            Text(if (state.enabled) "Hide" else "Show")
          }
          TextButton(onClick = onClear) {
            Text("Clear")
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedTextField(
          value = state.searchQuery,
          onValueChange = onQueryChange,
          label = { Text("Search title") },
          singleLine = true,
          modifier = Modifier.weight(1f),
        )
        Button(
          onClick = onSearch,
          enabled = state.searchQuery.isNotBlank() && !state.isSearching && !state.isAutoMatching,
        ) {
          if (state.isSearching) {
            CircularProgressIndicator(
              modifier = Modifier.padding(end = MaterialTheme.spacing.extraSmall),
              strokeWidth = 2.dp,
            )
          } else {
            Icon(Icons.Default.Search, contentDescription = null)
          }
        }
      }

      if (state.errorMessage != null) {
        Text(
          text = state.errorMessage,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
      }

      if (state.isAutoMatching) {
        Text(
          text = "Matching danmaku...",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.outline,
        )
      }

      if (state.isSearching || state.isLoadingEpisodes || state.isLoadingComments || state.isAutoMatching) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
      }

      if (state.selectedAnime != null) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = state.selectedAnime.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
          TextButton(onClick = onBackToResults) {
            Text("Results")
          }
        }

        DanmakuEpisodeList(
          episodes = state.episodeResults,
          enabled = !state.isLoadingComments,
          onLoadEpisode = onLoadEpisode,
        )
      } else {
        DanmakuAnimeList(
          animes = state.animeResults,
          enabled = !state.isLoadingEpisodes,
          onSelectAnime = onSelectAnime,
        )
      }
    }
  }
}

@Composable
private fun DanmakuAnimeList(
  animes: List<DanmakuAnime>,
  enabled: Boolean,
  onSelectAnime: (DanmakuAnime) -> Unit,
) {
  LazyColumn(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(max = 420.dp),
  ) {
    items(animes, key = { it.bangumiId }) { anime ->
      ListItem(
        headlineContent = {
          Text(anime.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
          anime.typeDescription?.takeIf { it.isNotBlank() }?.let {
            Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        },
        modifier = Modifier.clickable(enabled = enabled) { onSelectAnime(anime) },
      )
      HorizontalDivider()
    }
  }
}

@Composable
private fun DanmakuEpisodeList(
  episodes: List<DanmakuEpisode>,
  enabled: Boolean,
  onLoadEpisode: (DanmakuEpisode) -> Unit,
) {
  LazyColumn(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(max = 420.dp),
  ) {
    items(episodes, key = { it.episodeId }) { episode ->
      ListItem(
        headlineContent = {
          Text(episode.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
          episode.number?.let {
            Text("Episode $it", maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        },
        modifier = Modifier.clickable(enabled = enabled) { onLoadEpisode(episode) },
      )
      HorizontalDivider()
    }
  }
}
