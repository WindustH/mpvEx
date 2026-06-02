package app.windusth.mpvdanmaku.ui.player.controls.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.windusth.mpvdanmaku.repository.danmaku.DanmakuComment
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun DanmakuOverlay(
  comments: List<DanmakuComment>,
  enabled: Boolean,
  positionSeconds: Float,
  fontSize: Float = 22f,
  opacity: Float = 0.85f,
  scrollTime: Float = 12f,
  fixedTime: Float = 5f,
  frameRate: Float = 60f,
  displayArea: Float = 0.82f,
  bold: Boolean = true,
  outline: Float = 2f,
  shadow: Float = 1f,
  paused: Boolean = false,
  playbackSpeed: Float = 1f,
  mergeEnabled: Boolean = true,
  mergeWindow: Float = 3f,
  mergeThreshold: Int = 3,
  modifier: Modifier = Modifier,
) {
  val processedComments = remember(comments, mergeEnabled, mergeWindow, mergeThreshold) {
    val merged = if (mergeEnabled) {
      mergeDuplicateDanmaku(comments, mergeWindow.coerceIn(0.5f, 30f), mergeThreshold.coerceIn(2, 100))
    } else {
      comments
    }
    assignDanmakuRows(merged)
  }

  if (!enabled || processedComments.isEmpty()) return

  val currentPosition by rememberUpdatedState(positionSeconds)
  val currentPaused by rememberUpdatedState(paused)
  val currentSpeed by rememberUpdatedState(playbackSpeed)

  var renderedPosition by remember { mutableStateOf(positionSeconds) }
  var lastSample by remember { mutableStateOf(positionSeconds) }

  LaunchedEffect(enabled, processedComments, frameRate) {
    val fps = frameRate.coerceIn(24f, 120f).roundToInt().coerceAtLeast(1)
    val frameIntervalNanos = 1_000_000_000L / fps
    var lastFrameNanos = 0L

    while (isActive && enabled && processedComments.isNotEmpty()) {
      val now = try {
        withFrameNanos { it }
      } catch (_: Exception) {
        break
      }
      
      if (lastFrameNanos == 0L) {
        lastFrameNanos = now
        renderedPosition = currentPosition.coerceAtLeast(0f)
        continue
      }

      val elapsedNanos = now - lastFrameNanos
      if (elapsedNanos >= frameIntervalNanos) {
        val deltaSeconds = (elapsedNanos / 1_000_000_000f).coerceAtMost(0.1f)
        lastFrameNanos = now

        val samplePosition = currentPosition.coerceAtLeast(0f)
        val speed = currentSpeed.coerceAtLeast(0f)

        if (currentPaused) {
          renderedPosition = samplePosition
        } else {
          val sampleChanged = abs(samplePosition - lastSample) > 0.02f
          val backwardsSeek = sampleChanged && samplePosition < lastSample - 0.3f
          val forwardsJump = abs(samplePosition - renderedPosition) > max(1.0f, speed * 0.5f)

          if (backwardsSeek || forwardsJump) {
            renderedPosition = samplePosition
          } else {
            val expectedAdvance = deltaSeconds * speed
            val drift = samplePosition - renderedPosition
            val correction = drift * deltaSeconds * 2.0f
            renderedPosition += expectedAdvance + correction
          }

          if (sampleChanged) {
            lastSample = samplePosition
          }
        }
      }
    }
  }

  BoxWithConstraints(
    modifier = modifier.clipToBounds(),
  ) {
    val sortedComments = remember(processedComments) {
      processedComments
        .sortedBy { it.time }
        .mapIndexed { index, comment ->
          RenderDanmakuItem(
            key = DanmakuRenderKey(
              index = index,
              time = comment.time,
              type = comment.type,
              color = comment.color,
              text = comment.text,
              repeatCount = comment.repeatCount,
              isSelf = comment.isSelf,
            ),
            comment = comment,
          )
        }
    }
    val density = LocalDensity.current
    val widthPx = with(density) { maxWidth.toPx() }
    val heightPx = with(density) { maxHeight.toPx() }
    val alpha = opacity.coerceIn(0f, 1f)
    val scrollDuration = scrollTime.coerceAtLeast(1f)
    val fixedDuration = fixedTime.coerceAtLeast(1f)
    val fontSp = fontSize.coerceIn(10f, 72f).sp
    val fontPx = with(density) { fontSp.toPx() }
    val rowHeightPx = fontPx * 1.28f
    val displayHeightPx = heightPx * displayArea.coerceIn(0.1f, 1f)
    val rollingRows = max(1, floor(displayHeightPx / rowHeightPx).toInt())
    val fixedRows = max(1, floor(displayHeightPx * 0.35f / rowHeightPx).toInt())

    val shadowColor = Color.Black.copy(alpha = (0.75f * alpha).coerceIn(0f, 1f))

    val textStyle = TextStyle(
      fontSize = fontSp,
      lineHeight = (fontSize.coerceIn(10f, 72f) * 1.18f).sp,
      fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
      shadow = Shadow(
        color = shadowColor,
        offset = Offset(shadow, shadow),
        blurRadius = outline.coerceAtLeast(0f) * 1.8f,
      ),
    )

    val visibleComments = remember(sortedComments, renderedPosition, fixedDuration, scrollDuration) {
      val maxDuration = max(scrollDuration, fixedDuration)
      val startIndex = sortedComments.lowerBoundByTime(renderedPosition - maxDuration)
      val result = ArrayList<RenderDanmakuItem>()

      for (index in startIndex until sortedComments.size) {
        val item = sortedComments[index]
        val comment = item.comment
        if (comment.time > renderedPosition) break
        val duration = if (comment.type == 4 || comment.type == 5) fixedDuration else scrollDuration
        if (renderedPosition <= comment.time + duration) {
          result.add(item)
        }
      }

      result
    }

    val selfBorderColor = Color(0xFF42A5F5).copy(alpha = alpha) // Blue border for self-sent

    visibleComments.forEach { item ->
      key(item.key) {
        val comment = item.comment
        val displayText = if (comment.repeatCount > 1) {
          "${comment.text}×${comment.repeatCount}"
        } else {
          comment.text
        }
        val baseColor = Color(0xFF000000L or comment.color)
        val textColor = baseColor.copy(alpha = alpha)

        val selfModifier = if (comment.isSelf) {
          Modifier
            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .border(1.5.dp, selfBorderColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp)
        } else {
          Modifier
        }

        if (comment.type == 4 || comment.type == 5) {
          val row = comment.row % fixedRows
          val y = if (comment.type == 4) {
            displayHeightPx - ((row + 1) * rowHeightPx)
          } else {
            row * rowHeightPx
          }

          Text(
            text = displayText,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
            style = textStyle,
            modifier = selfModifier
              .fillMaxWidth()
              .graphicsLayer { translationY = y },
          )
        } else {
          val row = comment.row % rollingRows
          val y = row * rowHeightPx
          val elapsed = (renderedPosition - comment.time).coerceIn(0f, scrollDuration)
          val progress = elapsed / scrollDuration
          val textWidthPx = estimateTextWidthPx(displayText, fontPx)
          val x = widthPx - progress * (widthPx + textWidthPx)

          Text(
            text = displayText,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Visible,
            style = textStyle,
            modifier = selfModifier
              .graphicsLayer {
                translationX = x
                translationY = y
              },
          )
        }
      }
    }
  }
}

private data class RenderDanmakuItem(
  val key: DanmakuRenderKey,
  val comment: DanmakuComment,
)

private data class DanmakuRenderKey(
  val index: Int,
  val time: Float,
  val type: Int,
  val color: Long,
  val text: String,
  val repeatCount: Int,
  val isSelf: Boolean,
)

private fun estimateTextWidthPx(text: String, fontPx: Float): Float {
  val units = text.sumOf { char ->
    when {
      char.code <= 0x007F -> 0.58
      char.code in 0xFF61..0xFF9F -> 0.58
      else -> 1.0
    }
  }
  return (units * fontPx).toFloat()
}

private fun List<RenderDanmakuItem>.lowerBoundByTime(target: Float): Int {
  var low = 0
  var high = size
  while (low < high) {
    val mid = (low + high) ushr 1
    if (this[mid].comment.time < target) {
      low = mid + 1
    } else {
      high = mid
    }
  }
  return low
}

internal fun mergeDuplicateDanmaku(comments: List<DanmakuComment>, window: Float, threshold: Int): List<DanmakuComment> {
  data class BucketKey(val type: Int, val normalizedText: String)

  fun normalize(text: String): String = text.trim().replace(Regex("\\s+"), " ")

  fun mergeOneTextGroup(group: List<DanmakuComment>): List<DanmakuComment> {
    if (group.size < threshold) return group
    val sorted = group.sortedBy { it.time }
    val merged = ArrayList<DanmakuComment>(sorted.size)
    var i = 0
    while (i < sorted.size) {
      val start = sorted[i]
      // Skip self-sent danmaku from merging
      if (start.isSelf) {
        merged.add(start)
        i++
        continue
      }
      var j = i
      while (j < sorted.size && sorted[j].time <= start.time + window) j++
      val count = j - i
      if (count >= threshold) {
        merged.add(start.copy(repeatCount = count))
        i = j
      } else {
        merged.add(start)
        i++
      }
    }
    return merged
  }

  return comments
    .groupBy { BucketKey(it.type, normalize(it.text)) }
    .flatMap { (_, group) -> mergeOneTextGroup(group) }
    .sortedBy { it.time }
}

internal fun assignDanmakuRows(comments: List<DanmakuComment>): List<DanmakuComment> {
  var rollingRow = 0
  var topRow = 0
  var bottomRow = 0

  return comments
    .sortedBy { it.time }
    .map { comment ->
      when (comment.type) {
        4 -> comment.copy(row = bottomRow++)
        5 -> comment.copy(row = topRow++)
        else -> comment.copy(row = rollingRow++)
      }
    }
}
