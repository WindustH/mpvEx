package app.windusth.mpvdanmaku.ui.player

import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVNode

class PlayerObserver(
  private val activity: PlayerActivity,
) : MPVLib.EventObserver {
  override fun eventProperty(property: String) {
    if (activity.shouldIgnoreMpvCallbacks()) return
    activity.runOnUiThread {
      if (activity.shouldIgnoreMpvCallbacks()) return@runOnUiThread
      activity.onObserverEvent(property)
    }
  }

  override fun eventProperty(
    property: String,
    value: Long,
  ) {
    if (activity.shouldIgnoreMpvCallbacks()) return
    activity.runOnUiThread {
      if (activity.shouldIgnoreMpvCallbacks()) return@runOnUiThread
      activity.onObserverEvent(property, value)
    }
  }

  override fun eventProperty(
    property: String,
    value: Boolean,
  ) {
    if (activity.shouldIgnoreMpvCallbacks()) return
    activity.runOnUiThread {
      if (activity.shouldIgnoreMpvCallbacks()) return@runOnUiThread
      activity.onObserverEvent(property, value)
    }
  }

  override fun eventProperty(
    property: String,
    value: String,
  ) {
    if (activity.shouldIgnoreMpvCallbacks()) return
    activity.runOnUiThread {
      if (activity.shouldIgnoreMpvCallbacks()) return@runOnUiThread
      activity.onObserverEvent(property, value)
    }
  }

  override fun eventProperty(
    property: String,
    value: Double,
  ) {
    if (activity.shouldIgnoreMpvCallbacks()) return
    activity.runOnUiThread {
      if (activity.shouldIgnoreMpvCallbacks()) return@runOnUiThread
      activity.onObserverEvent(property, value)
    }
  }

  override fun eventProperty(
    property: String,
    value: MPVNode,
  ) {
    if (activity.shouldIgnoreMpvCallbacks()) return
    activity.runOnUiThread {
      if (activity.shouldIgnoreMpvCallbacks()) return@runOnUiThread
      activity.onObserverEvent(property, value)
    }
  }

  override fun event(eventId: Int, data: MPVNode) {
    if (activity.shouldIgnoreMpvCallbacks()) return
    activity.runOnUiThread {
      if (activity.shouldIgnoreMpvCallbacks()) return@runOnUiThread
      activity.event(eventId)
    }
  }
}
