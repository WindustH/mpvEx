package app.marlboroadvance.mpvex.preferences

import app.marlboroadvance.mpvex.preferences.preference.PreferenceStore

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
}
