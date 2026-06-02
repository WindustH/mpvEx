package app.windusth.mpvdanmuku.preferences

import app.windusth.mpvdanmuku.preferences.preference.PreferenceStore

class DanmakuPreferences(
  preferenceStore: PreferenceStore,
) {
  val autoMatch = preferenceStore.getBoolean("danmaku_auto_match", true)
  val enabled = preferenceStore.getBoolean("danmaku_enabled", false)
  val fontSize = preferenceStore.getFloat("danmaku_font_size", 22f)
  val opacity = preferenceStore.getFloat("danmaku_opacity", 0.85f)
  val scrollTime = preferenceStore.getFloat("danmaku_scroll_time", 12f)
  val fixedTime = preferenceStore.getFloat("danmaku_fixed_time", 5f)
  val frameRate = preferenceStore.getFloat("danmaku_frame_rate", 60f)
  val displayArea = preferenceStore.getFloat("danmaku_display_area", 0.82f)
  val bold = preferenceStore.getBoolean("danmaku_bold", true)
  val outline = preferenceStore.getFloat("danmaku_outline", 2f)
  val shadow = preferenceStore.getFloat("danmaku_shadow", 1f)
  val mergeDuplicates = preferenceStore.getBoolean("danmaku_merge_duplicates", true)
  val mergeDuplicateWindow = preferenceStore.getFloat("danmaku_merge_duplicate_window", 3f)
  val mergeDuplicateThreshold = preferenceStore.getInt("danmaku_merge_duplicate_threshold", 3)
  val sendMode = preferenceStore.getInt("danmaku_send_mode", 1)
  val sendColor = preferenceStore.getInt("danmaku_send_color", 0xFFFFFF)

  val dandanplayAppId = preferenceStore.getString("danmaku_dandanplay_app_id", "")
  val dandanplayAppSecret = preferenceStore.getString("danmaku_dandanplay_app_secret", "")
  val dandanplayOAuthRedirectUri = preferenceStore.getString(
    "danmaku_dandanplay_oauth_redirect_uri",
    "mpvdanmuku://dandanplay/oauth",
  )
  val dandanplayOAuthScope = preferenceStore.getString("danmaku_dandanplay_oauth_scope", "basic profile")
  val dandanplayCommentProxyUrl = preferenceStore.getString("danmaku_dandanplay_comment_proxy_url", "")

  // LLM-based filename parsing
  val llmApiBaseUrl = preferenceStore.getString("danmaku_llm_api_base_url", "")
  val llmApiKey = preferenceStore.getString("danmaku_llm_api_key", "")
  val llmModel = preferenceStore.getString("danmaku_llm_model", "gpt-4o-mini")
}
