package app.windusth.mpvdanmaku.preferences

import app.windusth.mpvdanmaku.preferences.preference.PreferenceStore

/**
 * Preferences for folder management
 */
class FoldersPreferences(
  preferenceStore: PreferenceStore,
) {
  // Set of folder paths that should be hidden from the folder list
  val blacklistedFolders = preferenceStore.getStringSet("blacklisted_folders", emptySet())
}
