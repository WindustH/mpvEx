package `is`.xyz.mpv

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

object FastThumbnails {
    @Volatile
    private var appContext: Context? = null

    @JvmStatic
    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    @JvmStatic
    fun isInitialized(): Boolean = appContext != null

    @JvmStatic
    fun clearCache() = Unit

    @JvmStatic
    @JvmOverloads
    fun generate(path: String, position: Double = 3.0, dimension: Int = 512, useHwDec: Boolean = false): Bitmap? {
        if (path.isBlank() || dimension <= 0) return null

        return runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                setDataSource(retriever, path)
                val frameTimeUs = max(0.0, position * 1_000_000.0).toLong()
                val bitmap = retriever.getFrameAtTime(frameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.frameAtTime
                    ?: return null
                bitmap.centerCrop(dimension)
            } finally {
                retriever.release()
            }
        }.getOrNull()
    }

    suspend fun generateAsync(path: String, position: Double, dimension: Int, useHwDec: Boolean): Bitmap? =
        withContext(Dispatchers.IO) { generate(path, position, dimension, useHwDec) }

    @JvmStatic
    @JvmOverloads
    fun generateMultiple(
        path: String,
        positions: List<Double>,
        dimension: Int = 512,
        useHwDec: Boolean = false,
    ): List<Bitmap> = positions.mapNotNull { generate(path, it, dimension, useHwDec) }

    suspend fun generateMultipleAsync(
        path: String,
        positions: List<Double>,
        dimension: Int,
        useHwDec: Boolean,
    ): List<Bitmap> = withContext(Dispatchers.IO) { generateMultiple(path, positions, dimension, useHwDec) }

    @JvmStatic
    @JvmOverloads
    fun benchmark(path: String, position: Double = 3.0, dimension: Int = 512, useHwDec: Boolean = false): Pair<Bitmap?, Long> {
        val start = System.currentTimeMillis()
        val bitmap = generate(path, position, dimension, useHwDec)
        return bitmap to (System.currentTimeMillis() - start)
    }

    private fun setDataSource(retriever: MediaMetadataRetriever, path: String) {
        val uri = Uri.parse(path)
        val context = appContext
        when {
            uri.scheme == "content" && context != null -> retriever.setDataSource(context, uri)
            uri.scheme == "http" || uri.scheme == "https" -> retriever.setDataSource(path, emptyMap())
            else -> retriever.setDataSource(path)
        }
    }

    private fun Bitmap.centerCrop(dimension: Int): Bitmap {
        val side = minOf(width, height)
        val x = ((width - side) / 2).coerceAtLeast(0)
        val y = ((height - side) / 2).coerceAtLeast(0)
        val cropped = Bitmap.createBitmap(this, x, y, side, side)
        val scaled = if (side == dimension) cropped else Bitmap.createScaledBitmap(cropped, dimension, dimension, true)
        if (cropped !== this && cropped !== scaled) cropped.recycle()
        return scaled
    }
}
