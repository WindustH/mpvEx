package `is`.xyz.mpv

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.abs

object Utils {
    data class Versions(
        val mpv: String,
        val buildDate: String,
        val libPlacebo: String,
        val ffmpeg: String,
    )

    val VERSIONS = Versions(
        mpv = "mpv-android",
        buildDate = "source build",
        libPlacebo = "unknown",
        ffmpeg = "unknown",
    )

    val PROTOCOLS = setOf(
        "file", "content", "http", "https", "data", "ftp",
        "rtmp", "rtmps", "rtp", "rtsp", "mms", "mmst", "mmsh", "tcp", "udp", "lavf",
    )

    fun copyAssets(context: Context) {
        copyAssetFile(context, "cacert.pem", File(context.filesDir, "cacert.pem"))
        writeFontsConf(context, File(context.filesDir, "fonts.conf"))
    }

    fun findRealPath(fd: Int): String? {
        var input: FileInputStream? = null
        return try {
            val path = File("/proc/self/fd/$fd").canonicalPath
            if (!path.startsWith("/proc") && File(path).canRead()) {
                input = FileInputStream(path)
                input.read()
                path
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            input?.close()
        }
    }

    fun prettyTime(d: Int, sign: Boolean = false): String {
        if (sign) return (if (d >= 0) "+" else "-") + prettyTime(abs(d), false)

        val hours = d / 3600
        val minutes = d % 3600 / 60
        val seconds = d % 60
        return if (hours == 0) {
            "%02d:%02d".format(minutes, seconds)
        } else {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        }
    }

    inline fun <reified T : Parcelable> getParcelableArray(bundle: Bundle, key: String): Array<T> {
        val values = if (Build.VERSION.SDK_INT >= 33) {
            bundle.getParcelableArray(key, T::class.java)
        } else {
            @Suppress("DEPRECATION")
            bundle.getParcelableArray(key)
        }
        return values?.filterIsInstance<T>()?.toTypedArray() ?: emptyArray()
    }

    private fun copyAssetFile(context: Context, name: String, outFile: File) {
        runCatching {
            context.assets.open(name).use { input ->
                if (outFile.length() == input.available().toLong()) return
                FileOutputStream(outFile).use { output -> input.copyTo(output) }
            }
        }
    }

    private fun writeFontsConf(context: Context, configFile: File) {
        runCatching {
            configFile.writeText(
                listOf(
                    "<fontconfig>",
                    "<dir>/system/fonts/</dir>",
                    "<dir>/product/fonts/</dir>",
                    "<cachedir>${context.cacheDir.path}</cachedir>",
                    "</fontconfig>",
                ).joinToString("\n")
            )
        }
    }
}
