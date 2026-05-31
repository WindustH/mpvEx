package app.windusth.mpvdanmuku.ui.player.controls.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.sp
import app.windusth.mpvdanmuku.repository.danmaku.DanmakuComment
import kotlinx.coroutines.isActive
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
  modifier: Modifier = Modifier,
) {
  if (!enabled || comments.isEmpty()) return

  var renderedPosition by remember { mutableStateOf(positionSeconds) }
  var anchorPosition by remember { mutableStateOf(positionSeconds) }
  var anchorFrameNanos by remember { mutableStateOf(0L) }

  LaunchedEffect(positionSeconds, paused, playbackSpeed) {
    val now = withFrameNanos { it }
    anchorPosition = positionSeconds
    anchorFrameNanos = now
    renderedPosition = positionSeconds
  }

  LaunchedEffect(enabled, comments, frameRate, paused, playbackSpeed) {
    val fps = frameRate.coerceIn(24f, 120f).roundToInt().coerceAtLeast(1)
    val frameIntervalNanos = 1_000_000_000L / fps
    var lastFrameNanos = 0L

    while (isActive && enabled && comments.isNotEmpty()) {
      val now = withFrameNanos { it }
      if (lastFrameNanos == 0L || now - lastFrameNanos >= frameIntervalNanos) {
        if (anchorFrameNanos == 0L) {
          anchorFrameNanos = now
          anchorPosition = positionSeconds
        }
        renderedPosition = if (paused) {
          anchorPosition
        } else {
          val elapsed = ((now - anchorFrameNanos).coerceAtLeast(0L) / 1_000_000_000f) * playbackSpeed.coerceAtLeast(0f)
          anchorPosition + elapsed
        }
        lastFrameNanos = now
      }
    }
  }

  BoxWithConstraints(
    modifier = modifier.clipToBounds(),
  ) {
    val sortedComments = remember(comments) { comments.sortedBy { it.time } }
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
      val result = ArrayList<DanmakuComment>()

      for (index in startIndex until sortedComments.size) {
        val comment = sortedComments[index]
        if (comment.time > renderedPosition) break
        val duration = if (comment.type == 4 || comment.type == 5) fixedDuration else scrollDuration
        if (renderedPosition <= comment.time + duration) {
          result.add(comment)
        }
      }

      result
    }

    visibleComments.forEach { comment ->
      val displayText = if (comment.repeatCount > 1) {
        "${comment.text}×${comment.repeatCount}"
      } else {
        comment.text
      }
      val baseColor = Color(0xFF000000L or comment.color)
      val textColor = baseColor.copy(alpha = alpha)

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
          modifier = Modifier
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
          modifier = Modifier
            .graphicsLayer {
              translationX = x
              translationY = y
            },
        )
      }
    }
  }
}

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

private fun List<DanmakuComment>.lowerBoundByTime(target: Float): Int {
  var low = 0
  var high = size
  while (low < high) {
    val mid = (low + high) ushr 1
    if (this[mid].time < target) {
      low = mid + 1
    } else {
      high = mid
    }
  }
  return low
}
