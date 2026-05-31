package app.windusth.mpvdanmuku.preferences

import android.content.Context
import android.content.SharedPreferences
import app.windusth.mpvdanmuku.repository.danmaku.DanmakuLoginResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DanmakuAuthStore(context: Context) {
  private val prefs: SharedPreferences =
    context.getSharedPreferences("danmaku_auth", Context.MODE_PRIVATE)

  val token: String?
    get() = prefs.getString(KEY_TOKEN, null)

  val userName: String?
    get() = prefs.getString(KEY_USER_NAME, null)

  val tokenExpireTime: String?
    get() = prefs.getString(KEY_TOKEN_EXPIRE_TIME, null)

  val isLoggedIn: Boolean
    get() {
      val t = token ?: return false
      if (t.isBlank()) return false
      val expireStr = tokenExpireTime ?: return true
      return try {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        val expireDate = fmt.parse(expireStr)
        expireDate != null && expireDate.after(Date())
      } catch (_: Exception) {
        false
      }
    }

  fun saveLogin(result: DanmakuLoginResult) {
    prefs.edit()
      .putString(KEY_TOKEN, result.token)
      .putString(KEY_USER_NAME, result.userName)
      .putString(KEY_TOKEN_EXPIRE_TIME, result.tokenExpireTime)
      .apply()
  }

  fun logout() {
    prefs.edit().clear().apply()
  }

  companion object {
    private const val KEY_TOKEN = "token"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_TOKEN_EXPIRE_TIME = "token_expire_time"
  }
}
