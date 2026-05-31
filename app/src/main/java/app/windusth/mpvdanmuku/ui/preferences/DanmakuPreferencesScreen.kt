package app.windusth.mpvdanmuku.ui.preferences

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import app.windusth.mpvdanmuku.presentation.Screen
import app.windusth.mpvdanmuku.preferences.DanmakuPreferences
import app.windusth.mpvdanmuku.preferences.preference.collectAsState
import app.windusth.mpvdanmuku.ui.theme.spacing
import app.windusth.mpvdanmuku.ui.utils.LocalBackStack
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

        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        ) {
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
