package app.windusth.mpvdanmaku.preferences

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DandanplayOAuthState(
  val accessToken: String? = null,
  val refreshToken: String? = null,
  val expiresAtMillis: Long = 0L,
  val scope: String? = null,
  val userName: String? = null,
  val pendingState: String? = null,
  val pendingCodeVerifier: String? = null,
) {
  val isAuthorized: Boolean
    get() = !accessToken.isNullOrBlank() || !refreshToken.isNullOrBlank()

  val isAccessTokenValid: Boolean
    get() = !accessToken.isNullOrBlank() &&
      (expiresAtMillis == 0L || expiresAtMillis > System.currentTimeMillis())
}

class DandanplayOAuthStore(context: Context) {
  private val prefs = context.getSharedPreferences("dandanplay_oauth", Context.MODE_PRIVATE)
  private val _state = MutableStateFlow(readState())

  val state: StateFlow<DandanplayOAuthState> = _state.asStateFlow()
  val current: DandanplayOAuthState
    get() = _state.value

  fun savePendingAuthorization(state: String, codeVerifier: String) {
    prefs.edit()
      .putString(KEY_PENDING_STATE, state)
      .putString(KEY_PENDING_CODE_VERIFIER, codeVerifier)
      .apply()
    refresh()
  }

  fun pendingCodeVerifierFor(state: String): String? {
    val current = current
    if (current.pendingState != state) return null
    return current.pendingCodeVerifier
  }

  fun clearPendingAuthorization() {
    prefs.edit()
      .remove(KEY_PENDING_STATE)
      .remove(KEY_PENDING_CODE_VERIFIER)
      .apply()
    refresh()
  }

  fun saveTokens(
    accessToken: String,
    refreshToken: String?,
    expiresAtMillis: Long,
    scope: String?,
    userName: String? = current.userName,
  ) {
    prefs.edit()
      .putString(KEY_ACCESS_TOKEN, accessToken)
      .putString(KEY_REFRESH_TOKEN, refreshToken)
      .putLong(KEY_EXPIRES_AT, expiresAtMillis)
      .putString(KEY_SCOPE, scope)
      .putString(KEY_USER_NAME, userName)
      .remove(KEY_PENDING_STATE)
      .remove(KEY_PENDING_CODE_VERIFIER)
      .apply()
    refresh()
  }

  fun logout() {
    prefs.edit().clear().apply()
    refresh()
  }

  private fun refresh() {
    _state.value = readState()
  }

  private fun readState(): DandanplayOAuthState =
    DandanplayOAuthState(
      accessToken = prefs.getString(KEY_ACCESS_TOKEN, null),
      refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null),
      expiresAtMillis = prefs.getLong(KEY_EXPIRES_AT, 0L),
      scope = prefs.getString(KEY_SCOPE, null),
      userName = prefs.getString(KEY_USER_NAME, null),
      pendingState = prefs.getString(KEY_PENDING_STATE, null),
      pendingCodeVerifier = prefs.getString(KEY_PENDING_CODE_VERIFIER, null),
    )

  companion object {
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_EXPIRES_AT = "expires_at_millis"
    private const val KEY_SCOPE = "scope"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_PENDING_STATE = "pending_state"
    private const val KEY_PENDING_CODE_VERIFIER = "pending_code_verifier"
  }
}
