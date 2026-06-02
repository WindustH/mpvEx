package app.windusth.mpvdanmuku.di

import app.windusth.mpvdanmuku.database.MpvDanmukuDatabase
import app.windusth.mpvdanmuku.preferences.AdvancedPreferences
import app.windusth.mpvdanmuku.preferences.AppearancePreferences
import app.windusth.mpvdanmuku.preferences.AudioPreferences
import app.windusth.mpvdanmuku.preferences.BrowserPreferences
import app.windusth.mpvdanmuku.preferences.DandanplayOAuthStore
import app.windusth.mpvdanmuku.preferences.DanmakuPreferences
import app.windusth.mpvdanmuku.preferences.DecoderPreferences
import app.windusth.mpvdanmuku.preferences.FoldersPreferences
import app.windusth.mpvdanmuku.preferences.GesturePreferences
import app.windusth.mpvdanmuku.preferences.PlayerPreferences
import app.windusth.mpvdanmuku.preferences.SettingsManager
import app.windusth.mpvdanmuku.preferences.SubtitlesPreferences
import app.windusth.mpvdanmuku.preferences.preference.AndroidPreferenceStore
import app.windusth.mpvdanmuku.preferences.preference.PreferenceStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val PreferencesModule =
  module {
    single { AndroidPreferenceStore(androidContext()) }.bind(PreferenceStore::class)

    single { AppearancePreferences(get()) }
    singleOf(::PlayerPreferences)
    singleOf(::GesturePreferences)
    singleOf(::DecoderPreferences)
    singleOf(::DanmakuPreferences)
    singleOf(::SubtitlesPreferences)
    singleOf(::AudioPreferences)
    singleOf(::AdvancedPreferences)
    single { BrowserPreferences(get(), androidContext()) }
    single { DandanplayOAuthStore(androidContext()) }
    singleOf(::FoldersPreferences)
    singleOf(::SettingsManager)
  }
