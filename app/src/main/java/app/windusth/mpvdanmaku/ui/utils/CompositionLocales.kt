package app.windusth.mpvdanmaku.ui.utils

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.navigation3.runtime.NavBackStack
import app.windusth.mpvdanmaku.presentation.Screen

val LocalBackStack: ProvidableCompositionLocal<NavBackStack<Screen>> =
  compositionLocalOf { error("LocalBackStack not initialized!") }
