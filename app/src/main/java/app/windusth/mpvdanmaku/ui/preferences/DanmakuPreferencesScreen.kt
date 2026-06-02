package app.windusth.mpvdanmaku.ui.preferences

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import app.windusth.mpvdanmaku.presentation.Screen
import app.windusth.mpvdanmaku.preferences.DandanplayOAuthStore
import app.windusth.mpvdanmaku.preferences.DanmakuPreferences
import app.windusth.mpvdanmaku.preferences.preference.collectAsState
import app.windusth.mpvdanmaku.repository.danmaku.DandanplayDanmakuRepository
import app.windusth.mpvdanmaku.ui.theme.spacing
import app.windusth.mpvdanmaku.ui.utils.LocalBackStack
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SliderPreference
import me.zhanghai.compose.preference.SwitchPreference
import org.koin.compose.koinInject
import kotlin.math.roundToInt

@Serializable
object DanmakuPreferencesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val backStack = LocalBackStack.current
    val preferences = koinInject<DanmakuPreferences>()
    val oauthStore = koinInject<DandanplayOAuthStore>()
    val repository = koinInject<DandanplayDanmakuRepository>()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Text(
              text = "Danmaku Settings",
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.primary,
            )
          },
          navigationIcon = {
            IconButton(onClick = backStack::removeLastOrNull) {
              Icon(
                Icons.AutoMirrored.Default.ArrowBack,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
              )
            }
          },
        )
      },
    ) { padding ->
      ProvidePreferenceLocals {
        val autoMatch by preferences.autoMatch.collectAsState()
        val enabled by preferences.enabled.collectAsState()
        val fontSize by preferences.fontSize.collectAsState()
        val opacity by preferences.opacity.collectAsState()
        val scrollTime by preferences.scrollTime.collectAsState()
        val fixedTime by preferences.fixedTime.collectAsState()
        val frameRate by preferences.frameRate.collectAsState()
        val displayArea by preferences.displayArea.collectAsState()
        val bold by preferences.bold.collectAsState()
        val outline by preferences.outline.collectAsState()
        val shadow by preferences.shadow.collectAsState()
        val mergeDuplicates by preferences.mergeDuplicates.collectAsState()
        val mergeDuplicateWindow by preferences.mergeDuplicateWindow.collectAsState()
        val mergeDuplicateThreshold by preferences.mergeDuplicateThreshold.collectAsState()
        val sendMode by preferences.sendMode.collectAsState()
        val sendColor by preferences.sendColor.collectAsState()
        val dandanplayAppId by preferences.dandanplayAppId.collectAsState()
        val dandanplayAppSecret by preferences.dandanplayAppSecret.collectAsState()
        val dandanplayOAuthRedirectUri by preferences.dandanplayOAuthRedirectUri.collectAsState()
        val dandanplayOAuthScope by preferences.dandanplayOAuthScope.collectAsState()
        val dandanplayCommentProxyUrl by preferences.dandanplayCommentProxyUrl.collectAsState()
        val oauthState by oauthStore.state.collectAsState()

        var oauthMessage by remember { mutableStateOf<String?>(null) }

        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        ) {
          item {
            PreferenceSectionHeader(title = "Dandanplay API")
          }

          item {
            PreferenceCard {
              Text(
                text = "Login and sending require an authorized dandanplay Open Platform AppId/AppSecret. Leave empty to use the built-in public API credentials for matching and loading only.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
              )

              Spacer(modifier = Modifier.height(8.dp))

              OutlinedTextField(
                value = dandanplayAppId,
                onValueChange = { preferences.dandanplayAppId.set(it) },
                label = { Text("AppId") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
              )

              Spacer(modifier = Modifier.height(8.dp))

              OutlinedTextField(
                value = dandanplayAppSecret,
                onValueChange = { preferences.dandanplayAppSecret.set(it) },
                label = { Text("AppSecret") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
              )

              Spacer(modifier = Modifier.height(8.dp))

              OutlinedTextField(
                value = dandanplayOAuthRedirectUri,
                onValueChange = { preferences.dandanplayOAuthRedirectUri.set(it) },
                label = { Text("OAuth Redirect URI") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
              )

              Spacer(modifier = Modifier.height(8.dp))

              OutlinedTextField(
                value = dandanplayOAuthScope,
                onValueChange = { preferences.dandanplayOAuthScope.set(it) },
                label = { Text("OAuth Scope") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
              )

              Spacer(modifier = Modifier.height(8.dp))

              OutlinedTextField(
                value = dandanplayCommentProxyUrl,
                onValueChange = { preferences.dandanplayCommentProxyUrl.set(it) },
                label = { Text("Comment Proxy URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
              )
            }
          }

          item {
            PreferenceSectionHeader(title = "Account")
          }

          item {
            PreferenceCard {
              if (oauthState.isAuthorized) {
                Text(
                  text = "Authorized via OAuth",
                  style = MaterialTheme.typography.bodyMedium,
                )
                oauthState.scope?.takeIf { it.isNotBlank() }?.let { scope ->
                  Text(
                    text = "Scope: $scope",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                  )
                }
              } else {
                Text(
                  text = "Authorize dandanplay in the browser to send danmaku with your account",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.outline,
                )
              }

              Spacer(modifier = Modifier.height(8.dp))

              Button(
                onClick = {
                  oauthMessage = null
                  coroutineScope.launch {
                    runCatching {
                      repository.createOAuthAuthorizationUrl()
                    }.onSuccess { url ->
                      context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }.onFailure { error ->
                      oauthMessage = error.message ?: "Failed to start authorization"
                    }
                  }
                },
                enabled = dandanplayAppId.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(if (oauthState.isAuthorized) "Reauthorize dandanplay" else "Authorize dandanplay")
              }

              Spacer(modifier = Modifier.height(8.dp))

              Button(
                onClick = {
                  oauthStore.logout()
                  oauthMessage = "Logged out"
                },
                enabled = oauthState.isAuthorized,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text("Logout")
              }

              oauthMessage?.let { message ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                  text = message,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.outline,
                )
              }
            }
          }

          item {
            PreferenceSectionHeader(title = "Sending")
          }

          item {
            PreferenceCard {
              SwitchPreference(
                value = sendMode == 1,
                onValueChange = { if (it) preferences.sendMode.set(1) },
                title = { Text("Send Mode: Scroll") },
                summary = {
                  Text(
                    "Danmaku scrolls across the screen",
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
              )

              PreferenceDivider()

              SwitchPreference(
                value = sendMode == 4,
                onValueChange = { if (it) preferences.sendMode.set(4) },
                title = { Text("Send Mode: Bottom") },
                summary = {
                  Text(
                    "Danmaku appears at the bottom",
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
              )

              PreferenceDivider()

              SwitchPreference(
                value = sendMode == 5,
                onValueChange = { if (it) preferences.sendMode.set(5) },
                title = { Text("Send Mode: Top") },
                summary = {
                  Text(
                    "Danmaku appears at the top",
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
              )

              PreferenceDivider()

              SliderPreference(
                value = sendColor.toFloat(),
                onValueChange = { preferences.sendColor.set(it.roundToInt().coerceIn(0, 0xFFFFFF)) },
                title = { Text("Default Color") },
                valueRange = 0f..16777215f,
                summary = {
                  Text(
                    "#%06X".format(sendColor),
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
                onSliderValueChange = { preferences.sendColor.set(it.roundToInt().coerceIn(0, 0xFFFFFF)) },
                sliderValue = sendColor.toFloat(),
              )
            }
          }

          item {
            PreferenceSectionHeader(title = "Auto Match")
          }

          item {
            PreferenceCard {
              SwitchPreference(
                value = autoMatch,
                onValueChange = preferences.autoMatch::set,
                title = { Text("Auto-match danmaku") },
                summary = {
                  Text(
                    "Automatically search and load danmaku when playing a video",
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
                icon = {
                  Icon(
                    Icons.Outlined.BlurOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                },
              )
            }
          }

          item {
            PreferenceSectionHeader(title = "LLM Parsing")
          }

          item {
            PreferenceCard {
              val llmApiBaseUrl by preferences.llmApiBaseUrl.collectAsState()
              val llmApiKey by preferences.llmApiKey.collectAsState()
              val llmModel by preferences.llmModel.collectAsState()

              var testResult by remember { mutableStateOf<String?>(null) }
              var isTesting by remember { mutableStateOf(false) }

              Text(
                text = "Configure an OpenAI-compatible LLM API for smarter filename parsing. When configured, this replaces regex-based parsing. Falls back to regex if unavailable.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
              )

              Spacer(modifier = Modifier.height(8.dp))

              OutlinedTextField(
                value = llmApiBaseUrl,
                onValueChange = { preferences.llmApiBaseUrl.set(it) },
                label = { Text("API Base URL") },
                placeholder = { Text("https://api.openai.com/v1") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
              )

              Spacer(modifier = Modifier.height(8.dp))

              OutlinedTextField(
                value = llmApiKey,
                onValueChange = { preferences.llmApiKey.set(it) },
                label = { Text("API Key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
              )

              Spacer(modifier = Modifier.height(8.dp))

              OutlinedTextField(
                value = llmModel,
                onValueChange = { preferences.llmModel.set(it) },
                label = { Text("Model") },
                placeholder = { Text("gpt-4o-mini") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
              )

              Spacer(modifier = Modifier.height(12.dp))

              Button(
                onClick = {
                  testResult = null
                  isTesting = true
                  coroutineScope.launch {
                    testResult = repository.testLlmConnection()
                    isTesting = false
                  }
                },
                enabled = !isTesting && llmApiBaseUrl.isNotBlank() && llmApiKey.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(if (isTesting) "Testing..." else "Test Connection")
              }

              if (testResult != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                  text = testResult!!,
                  style = MaterialTheme.typography.bodySmall,
                  color = if (testResult!!.startsWith("Error")) {
                    MaterialTheme.colorScheme.error
                  } else {
                    MaterialTheme.colorScheme.primary
                  },
                )
              }
            }
          }

          item {
            PreferenceSectionHeader(title = "Display")
          }

          item {
            PreferenceCard {
              SwitchPreference(
                value = enabled,
                onValueChange = preferences.enabled::set,
                title = { Text("Show danmaku by default") },
                summary = {
                  Text(
                    "When auto-loaded, danmaku will be shown automatically",
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
              )

              PreferenceDivider()

              SliderPreference(
                value = fontSize,
                onValueChange = { preferences.fontSize.set(it.roundToInt().toFloat()) },
                title = { Text("Font Size") },
                valueRange = 10f..60f,
                summary = {
                  Text(
                    "%.0f sp".format(fontSize),
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
                onSliderValueChange = { preferences.fontSize.set(it.roundToInt().toFloat()) },
                sliderValue = fontSize,
              )

              PreferenceDivider()

              SliderPreference(
                value = opacity,
                onValueChange = { preferences.opacity.set(it.coerceIn(0.1f, 1f)) },
                title = { Text("Opacity") },
                valueRange = 0.1f..1f,
                summary = {
                  Text(
                    "%.0f%%".format(opacity * 100),
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
                onSliderValueChange = { preferences.opacity.set(it.coerceIn(0.1f, 1f)) },
                sliderValue = opacity,
              )

              PreferenceDivider()

              SliderPreference(
                value = scrollTime,
                onValueChange = { preferences.scrollTime.set(it.coerceIn(3f, 30f)) },
                title = { Text("Scroll Speed") },
                valueRange = 3f..30f,
                summary = {
                  Text(
                    "%.1f sec".format(scrollTime),
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
                onSliderValueChange = { preferences.scrollTime.set(it.coerceIn(3f, 30f)) },
                sliderValue = scrollTime,
              )

              PreferenceDivider()

              SliderPreference(
                value = fixedTime,
                onValueChange = { preferences.fixedTime.set(it.coerceIn(1f, 15f)) },
                title = { Text("Fixed Duration") },
                valueRange = 1f..15f,
                summary = {
                  Text(
                    "%.1f sec".format(fixedTime),
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
                onSliderValueChange = { preferences.fixedTime.set(it.coerceIn(1f, 15f)) },
                sliderValue = fixedTime,
              )

              PreferenceDivider()

              SliderPreference(
                value = frameRate,
                onValueChange = { preferences.frameRate.set(it.roundToInt().toFloat().coerceIn(24f, 120f)) },
                title = { Text("Frame Rate") },
                valueRange = 24f..120f,
                summary = {
                  Text(
                    "%.0f fps".format(frameRate),
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
                onSliderValueChange = { preferences.frameRate.set(it.roundToInt().toFloat().coerceIn(24f, 120f)) },
                sliderValue = frameRate,
              )
            }
          }

          item {
            PreferenceSectionHeader(title = "Duplicate Merging")
          }

          item {
            PreferenceCard {
              SwitchPreference(
                value = mergeDuplicates,
                onValueChange = preferences.mergeDuplicates::set,
                title = { Text("Merge duplicate danmaku") },
                summary = {
                  Text(
                    "Combine repeated comments in a short time window, e.g. Hello×5",
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
              )

              PreferenceDivider()

              SliderPreference(
                value = mergeDuplicateWindow,
                onValueChange = { preferences.mergeDuplicateWindow.set(it.coerceIn(1f, 10f)) },
                title = { Text("Merge Window") },
                valueRange = 1f..10f,
                summary = {
                  Text(
                    "%.1f sec".format(mergeDuplicateWindow),
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
                onSliderValueChange = { preferences.mergeDuplicateWindow.set(it.coerceIn(1f, 10f)) },
                sliderValue = mergeDuplicateWindow,
              )

              PreferenceDivider()

              SliderPreference(
                value = mergeDuplicateThreshold.toFloat(),
                onValueChange = { preferences.mergeDuplicateThreshold.set(it.roundToInt().coerceIn(2, 20)) },
                title = { Text("Merge Threshold") },
                valueRange = 2f..20f,
                summary = {
                  Text(
                    "$mergeDuplicateThreshold comments",
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
                onSliderValueChange = { preferences.mergeDuplicateThreshold.set(it.roundToInt().coerceIn(2, 20)) },
                sliderValue = mergeDuplicateThreshold.toFloat(),
              )
            }
          }

          item {
            PreferenceSectionHeader(title = "Style")
          }

          item {
            PreferenceCard {
              SwitchPreference(
                value = bold,
                onValueChange = preferences.bold::set,
                title = { Text("Bold") },
              )

              PreferenceDivider()

              SliderPreference(
                value = outline,
                onValueChange = { preferences.outline.set(it.coerceIn(0f, 4f)) },
                title = { Text("Outline") },
                valueRange = 0f..4f,
                summary = {
                  Text(
                    "%.1f".format(outline),
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
                onSliderValueChange = { preferences.outline.set(it.coerceIn(0f, 4f)) },
                sliderValue = outline,
              )

              PreferenceDivider()

              SliderPreference(
                value = shadow,
                onValueChange = { preferences.shadow.set(it.coerceIn(0f, 4f)) },
                title = { Text("Shadow") },
                valueRange = 0f..4f,
                summary = {
                  Text(
                    "%.1f".format(shadow),
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
                onSliderValueChange = { preferences.shadow.set(it.coerceIn(0f, 4f)) },
                sliderValue = shadow,
              )

              PreferenceDivider()

              SliderPreference(
                value = displayArea,
                onValueChange = { preferences.displayArea.set(it.coerceIn(0.3f, 1f)) },
                title = { Text("Display Area") },
                valueRange = 0.3f..1f,
                summary = {
                  Text(
                    "%.0f%%".format(displayArea * 100),
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
                onSliderValueChange = { preferences.displayArea.set(it.coerceIn(0.3f, 1f)) },
                sliderValue = displayArea,
              )
            }
          }
        }
      }
    }
  }
}
