package app.marlboroadvance.mpvex.ui.player

import app.marlboroadvance.mpvex.repository.danmaku.DanmakuAnime
import app.marlboroadvance.mpvex.repository.danmaku.DanmakuComment
import app.marlboroadvance.mpvex.repository.danmaku.DanmakuEpisode

data class DanmakuUiState(
  val searchQuery: String = "",
  val animeResults: List<DanmakuAnime> = emptyList(),
  val episodeResults: List<DanmakuEpisode> = emptyList(),
  val selectedAnime: DanmakuAnime? = null,
  val comments: List<DanmakuComment> = emptyList(),
  val enabled: Boolean = false,
  val loadedLabel: String? = null,
  val isSearching: Boolean = false,
  val isLoadingEpisodes: Boolean = false,
  val isLoadingComments: Boolean = false,
  val isAutoMatching: Boolean = false,
  val errorMessage: String? = null,
)
