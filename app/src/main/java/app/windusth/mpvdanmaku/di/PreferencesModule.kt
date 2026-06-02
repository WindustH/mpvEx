package app.windusth.mpvdanmaku.di

import app.windusth.mpvdanmaku.database.MpvDanmakuDatabase
import app.windusth.mpvdanmaku.preferences.AdvancedPreferences
import app.windusth.mpvdanmaku.preferences.AppearancePreferences
import app.windusth.mpvdanmaku.preferences.AudioPreferences
import app.windusth.mpvdanmaku.preferences.BrowserPreferences
import app.windusth.mpvdanmaku.preferences.DandanplayOAuthStore
import app.windusth.mpvdanmaku.preferences.DanmakuPreferences
import app.windusth.mpvdanmaku.preferences.DecoderPreferences
import app.windusth.mpvdanmaku.preferences.FoldersPreferences
import app.windusth.mpvdanmaku.preferences.GesturePreferences
import app.windusth.mpvdanmaku.preferences.PlayerPreferences
import app.windusth.mpvdanmaku.preferences.SettingsManager
import app.windusth.mpvdanmaku.preferences.SubtitlesPreferences
import app.windusth.mpvdanmaku.preferences.preference.AndroidPreferenceStore
import app.windusth.mpvdanmaku.preferences.preference.PreferenceStore
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
