package app.windusth.mpvdanmuku.ui.utils

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.navigation3.runtime.NavBackStack
import app.windusth.mpvdanmuku.presentation.Screen

val LocalBackStack: ProvidableCompositionLocal<NavBackStack<Screen>> =
  compositionLocalOf { error("LocalBackStack not initialized!") }
