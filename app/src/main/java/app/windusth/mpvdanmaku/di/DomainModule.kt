package app.windusth.mpvdanmaku.di

import app.windusth.mpvdanmaku.repository.danmaku.DandanplayDanmakuRepository
import app.windusth.mpvdanmaku.repository.wyzie.WyzieSearchRepository
import okhttp3.OkHttpClient
import org.koin.dsl.module
import org.koin.android.ext.koin.androidContext
import java.util.concurrent.TimeUnit

val domainModule = module {
    single {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    single { DandanplayDanmakuRepository(get(), get(), get(), get()) }
    single { WyzieSearchRepository(androidContext(), get(), get(), get()) }
}
