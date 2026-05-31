package `is`.xyz.mpv

import android.content.Context
import android.graphics.Bitmap
import android.view.Surface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("unused")
object MPVLib {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val propertyReadsEnabled = AtomicBoolean(false)

    init {
        arrayOf("mpv", "player").forEach { System.loadLibrary(it) }
    }

    external fun create(appctx: Context)
    external fun init()
    external fun destroy()
    external fun attachSurface(surface: Surface)
    external fun detachSurface()

    external fun command(vararg cmd: String)

    external fun setOptionString(name: String, value: String): Int

    external fun grabThumbnail(dimension: Int): Bitmap?

    external fun getPropertyInt(property: String): Int?
    external fun setPropertyInt(property: String, value: Int)
    external fun getPropertyDouble(property: String): Double?
    external fun setPropertyDouble(property: String, value: Double)
    external fun getPropertyBoolean(property: String): Boolean?
    external fun setPropertyBoolean(property: String, value: Boolean)
    external fun getPropertyString(property: String): String?
    external fun setPropertyString(property: String, value: String)

    external fun observeProperty(property: String, format: Int)

    @JvmStatic
    fun enablePropertyReads() {
        propertyReadsEnabled.set(true)
        propInt.observeExisting()
        propLong.observeExisting()
        propBoolean.observeExisting()
        propString.observeExisting()
        propDouble.observeExisting()
        propFloat.observeExisting()
        propNode.observeExisting()
    }

    @JvmStatic
    fun disablePropertyReads() {
        propertyReadsEnabled.set(false)
    }

    @JvmStatic
    fun isMpvAvailable(): Boolean = propertyReadsEnabled.get()

    fun commandNode(vararg cmd: String): MPVNode? {
        command(*cmd)
        return MPVNode.None
    }

    fun getPropertyNode(property: String): MPVNode? =
        when (property) {
            "track-list" -> buildTrackListNode()
            "chapter-list" -> buildChapterListNode()
            else -> getPropertyString(property)?.let { MPVNode.StringNode(it) }
        }

    fun setPropertyNode(property: String, value: MPVNode) {
        setPropertyString(property, value.toJson())
    }

    @JvmStatic
    fun getPropertyFloat(property: String): Float? = getPropertyDouble(property)?.toFloat()

    @JvmStatic
    fun setPropertyFloat(property: String, value: Float) = setPropertyDouble(property, value.toDouble())

    @JvmStatic
    fun getPropertyLong(property: String): Long? = getPropertyInt(property)?.toLong()

    @JvmStatic
    fun setPropertyLong(property: String, value: Long) = setPropertyInt(property, value.toInt())

    class Property<T>(
        val type: Int,
        private val getter: (String) -> T?,
        private val setter: ((String, T) -> Unit)? = null,
        private val poll: Boolean = false,
    ) {
        private val states = ConcurrentHashMap<String, MutableStateFlow<T?>>()

        operator fun get(property: String): StateFlow<T?> {
            val existing = states[property]
            if (existing != null) return existing

            val state = MutableStateFlow(read(property))
            val previous = states.putIfAbsent(property, state)
            if (previous != null) return previous

            if (poll) {
                MPVLib.scope.launch {
                    while (true) {
                        if (MPVLib.isMpvAvailable()) {
                            state.value = read(property)
                        }
                        delay(500)
                    }
                }
            } else if (MPVLib.isMpvAvailable()) {
                runCatching { MPVLib.observeProperty(property, type) }
            }
            return state
        }

        operator fun set(property: String, value: T) {
            if (MPVLib.isMpvAvailable()) {
                setter?.invoke(property, value)
            }
            emit(property, value)
        }

        fun emit(property: String, value: T?) {
            states[property]?.value = value
        }

        fun refresh(property: String) {
            states[property]?.value = read(property)
        }

        fun observeExisting() {
            if (!poll && MPVLib.isMpvAvailable()) {
                states.keys.forEach { property ->
                    runCatching { MPVLib.observeProperty(property, type) }
                }
            }
        }

        private fun read(property: String): T? =
            if (MPVLib.isMpvAvailable()) {
                runCatching { getter(property) }.getOrNull()
            } else {
                null
            }
    }

    val propInt = Property(MpvFormat.MPV_FORMAT_INT64, ::getPropertyInt, ::setPropertyInt)
    val propLong = Property(MpvFormat.MPV_FORMAT_INT64, ::getPropertyLong, ::setPropertyLong)
    val propBoolean = Property(MpvFormat.MPV_FORMAT_FLAG, ::getPropertyBoolean, ::setPropertyBoolean)
    val propString = Property(MpvFormat.MPV_FORMAT_STRING, ::getPropertyString, ::setPropertyString)
    val propDouble = Property(MpvFormat.MPV_FORMAT_DOUBLE, ::getPropertyDouble, ::setPropertyDouble)
    val propFloat = Property(MpvFormat.MPV_FORMAT_DOUBLE, ::getPropertyFloat, ::setPropertyFloat)
    val propNode = Property(MpvFormat.MPV_FORMAT_NODE, ::getPropertyNode, ::setPropertyNode, poll = true)

    private val observers = mutableListOf<EventObserver>()
    private val logObservers = mutableListOf<LogObserver>()
    private val eventPropertyFlow = MutableSharedFlow<Pair<String, Any?>>(extraBufferCapacity = 64)
    private val eventIdFlow = MutableSharedFlow<Pair<Int, MPVNode>>(extraBufferCapacity = 64)
    val logFlow = MutableSharedFlow<Triple<String, Int, String>>(extraBufferCapacity = 64)

    @JvmStatic
    fun addObserver(o: EventObserver) {
        synchronized(observers) { observers.add(o) }
    }

    @JvmStatic
    fun removeObserver(o: EventObserver) {
        synchronized(observers) { observers.remove(o) }
    }

    @JvmStatic
    fun eventProperty(property: String, value: Long) {
        propInt.emit(property, value.toInt())
        propLong.emit(property, value)
        emitProperty(property, value) { it.eventProperty(property, value) }
    }

    @JvmStatic
    fun eventProperty(property: String, value: Boolean) {
        propBoolean.emit(property, value)
        emitProperty(property, value) { it.eventProperty(property, value) }
    }

    @JvmStatic
    fun eventProperty(property: String, value: Double) {
        propDouble.emit(property, value)
        propFloat.emit(property, value.toFloat())
        emitProperty(property, value) { it.eventProperty(property, value) }
    }

    @JvmStatic
    fun eventProperty(property: String, value: String) {
        propString.emit(property, value)
        emitProperty(property, value) { it.eventProperty(property, value) }
    }

    @JvmStatic
    fun eventProperty(property: String, value: MPVNode) {
        propNode.emit(property, value)
        emitProperty(property, value) { it.eventProperty(property, value) }
    }

    @JvmStatic
    fun eventProperty(property: String) {
        refreshNodeProperties(property)
        eventPropertyFlow.tryEmit(property to null)
        synchronized(observers) {
            observers.toList().forEach { it.eventProperty(property) }
        }
    }

    @JvmStatic
    fun event(eventId: Int) {
        val data = MPVNode.None
        if (eventId == MpvEvent.MPV_EVENT_FILE_LOADED || eventId == MpvEvent.MPV_EVENT_VIDEO_RECONFIG) {
            propNode.refresh("track-list")
            propNode.refresh("chapter-list")
        }
        eventIdFlow.tryEmit(eventId to data)
        synchronized(observers) {
            observers.toList().forEach { it.event(eventId, data) }
        }
    }

    fun event(eventId: Int, data: MPVNode) {
        eventIdFlow.tryEmit(eventId to data)
        synchronized(observers) {
            observers.toList().forEach { it.event(eventId, data) }
        }
    }

    fun eventFlow(property: String): Flow<Unit> =
        eventPropertyFlow.filter { it.first == property }.map { Unit }

    fun eventFlow(eventId: Int): Flow<Unit> =
        eventIdFlow.filter { it.first == eventId }.map { Unit }

    @JvmStatic
    fun addLogObserver(o: LogObserver) {
        synchronized(logObservers) { logObservers.add(o) }
    }

    @JvmStatic
    fun removeLogObserver(o: LogObserver) {
        synchronized(logObservers) { logObservers.remove(o) }
    }

    @JvmStatic
    fun logMessage(prefix: String, level: Int, text: String) {
        logFlow.tryEmit(Triple(prefix, level, text))
        synchronized(logObservers) {
            logObservers.toList().forEach { it.logMessage(prefix, level, text) }
        }
    }

    private fun emitProperty(property: String, value: Any?, notify: (EventObserver) -> Unit) {
        refreshNodeProperties(property)
        eventPropertyFlow.tryEmit(property to value)
        synchronized(observers) {
            observers.toList().forEach(notify)
        }
    }

    private fun refreshNodeProperties(property: String) {
        if (property.startsWith("track-list")) propNode.refresh("track-list")
        if (property.startsWith("chapter-list")) propNode.refresh("chapter-list")
    }

    private fun buildTrackListNode(): MPVNode {
        val count = getPropertyInt("track-list/count") ?: return MPVNode.ArrayNode(emptyList())
        val items = (0 until count).mapNotNull { index ->
            val id = getPropertyInt("track-list/$index/id") ?: return@mapNotNull null
            val type = getPropertyString("track-list/$index/type") ?: return@mapNotNull null
            jsonObject(
                stringField("type", type),
                numberField("id", id),
                stringField("title", getPropertyString("track-list/$index/title")),
                stringField("lang", getPropertyString("track-list/$index/lang")),
                booleanField("image", getPropertyBoolean("track-list/$index/image")),
                booleanField("albumArt", getPropertyBoolean("track-list/$index/albumart")),
                booleanField("default", getPropertyBoolean("track-list/$index/default")),
                booleanField("forced", getPropertyBoolean("track-list/$index/forced")),
                booleanField("dependent", getPropertyBoolean("track-list/$index/dependent")),
                booleanField("visual-impaired", getPropertyBoolean("track-list/$index/visual-impaired")),
                booleanField("hearing-impaired", getPropertyBoolean("track-list/$index/hearing-impaired")),
                booleanField("selected", getPropertyBoolean("track-list/$index/selected")),
                booleanField("external", getPropertyBoolean("track-list/$index/external")),
                stringField("external-filename", getPropertyString("track-list/$index/external-filename")),
                stringField("codec", getPropertyString("track-list/$index/codec")),
                stringField("codec-desc", getPropertyString("track-list/$index/codec-desc")),
                stringField("codec-profile", getPropertyString("track-list/$index/codec-profile")),
                stringField("decoder", getPropertyString("track-list/$index/decoder")),
                stringField("decoder-desc", getPropertyString("track-list/$index/decoder-desc")),
                stringField("demux-channels", getPropertyString("track-list/$index/demux-channels")),
                stringField("format-name", getPropertyString("track-list/$index/format-name")),
                longField("src-id", getPropertyInt("track-list/$index/src-id")?.toLong()),
                longField("hls-bitrate", getPropertyInt("track-list/$index/hls-bitrate")?.toLong()),
                longField("program-id", getPropertyInt("track-list/$index/program-id")?.toLong()),
                longField("main-selection", getPropertyInt("track-list/$index/main-selection")?.toLong()),
                longField("ff-index", getPropertyInt("track-list/$index/ff-index")?.toLong()),
                longField("demux-w", getPropertyInt("track-list/$index/demux-w")?.toLong()),
                longField("demux-h", getPropertyInt("track-list/$index/demux-h")?.toLong()),
                longField("demux-channel-count", getPropertyInt("track-list/$index/demux-channel-count")?.toLong()),
                longField("demux-samplerate", getPropertyInt("track-list/$index/demux-samplerate")?.toLong()),
                doubleField("demux-fps", getPropertyDouble("track-list/$index/demux-fps")),
            )
        }
        return MPVNode.JsonNode(items.joinToString(prefix = "[", postfix = "]", separator = ","))
    }

    private fun buildChapterListNode(): MPVNode {
        val count = getPropertyInt("chapter-list/count") ?: return MPVNode.ArrayNode(emptyList())
        val items = (0 until count).map {
            jsonObject(
                doubleField("time", getPropertyDouble("chapter-list/$it/time")),
                stringField("title", getPropertyString("chapter-list/$it/title") ?: ""),
            )
        }
        return MPVNode.JsonNode(items.joinToString(prefix = "[", postfix = "]", separator = ","))
    }

    private fun jsonObject(vararg fields: String?): String =
        fields.filterNotNull().joinToString(prefix = "{", postfix = "}", separator = ",")

    private fun stringField(name: String, value: String?): String? =
        value?.let { "\"${jsonEscape(name)}\":\"${jsonEscape(it)}\"" }

    private fun numberField(name: String, value: Int?): String? =
        value?.let { "\"${jsonEscape(name)}\":$it" }

    private fun longField(name: String, value: Long?): String? =
        value?.let { "\"${jsonEscape(name)}\":$it" }

    private fun doubleField(name: String, value: Double?): String? =
        value?.let { "\"${jsonEscape(name)}\":$it" }

    private fun booleanField(name: String, value: Boolean?): String? =
        value?.let { "\"${jsonEscape(name)}\":$it" }

    private fun jsonEscape(value: String): String =
        buildString(value.length + 8) {
            value.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> {
                        if (ch.code < 0x20) append("\\u%04x".format(ch.code)) else append(ch)
                    }
                }
            }
        }

    interface EventObserver {
        fun eventProperty(property: String)
        fun eventProperty(property: String, value: Long)
        fun eventProperty(property: String, value: Boolean)
        fun eventProperty(property: String, value: String)
        fun eventProperty(property: String, value: Double)
        fun eventProperty(property: String, value: MPVNode)
        fun event(eventId: Int, data: MPVNode)
    }

    interface LogObserver {
        fun logMessage(prefix: String, level: Int, text: String)
    }

    object MpvFormat {
        const val MPV_FORMAT_NONE: Int = 0
        const val MPV_FORMAT_STRING: Int = 1
        const val MPV_FORMAT_OSD_STRING: Int = 2
        const val MPV_FORMAT_FLAG: Int = 3
        const val MPV_FORMAT_INT64: Int = 4
        const val MPV_FORMAT_DOUBLE: Int = 5
        const val MPV_FORMAT_NODE: Int = 6
        const val MPV_FORMAT_NODE_ARRAY: Int = 7
        const val MPV_FORMAT_NODE_MAP: Int = 8
        const val MPV_FORMAT_BYTE_ARRAY: Int = 9
    }

    object MpvEvent {
        const val MPV_EVENT_NONE: Int = 0
        const val MPV_EVENT_SHUTDOWN: Int = 1
        const val MPV_EVENT_LOG_MESSAGE: Int = 2
        const val MPV_EVENT_GET_PROPERTY_REPLY: Int = 3
        const val MPV_EVENT_SET_PROPERTY_REPLY: Int = 4
        const val MPV_EVENT_COMMAND_REPLY: Int = 5
        const val MPV_EVENT_START_FILE: Int = 6
        const val MPV_EVENT_END_FILE: Int = 7
        const val MPV_EVENT_FILE_LOADED: Int = 8
        const val MPV_EVENT_IDLE: Int = 11
        const val MPV_EVENT_TICK: Int = 14
        const val MPV_EVENT_CLIENT_MESSAGE: Int = 16
        const val MPV_EVENT_VIDEO_RECONFIG: Int = 17
        const val MPV_EVENT_AUDIO_RECONFIG: Int = 18
        const val MPV_EVENT_SEEK: Int = 20
        const val MPV_EVENT_PLAYBACK_RESTART: Int = 21
        const val MPV_EVENT_PROPERTY_CHANGE: Int = 22
        const val MPV_EVENT_QUEUE_OVERFLOW: Int = 24
        const val MPV_EVENT_HOOK: Int = 25
    }

    object MpvLogLevel {
        const val MPV_LOG_LEVEL_NONE: Int = 0
        const val MPV_LOG_LEVEL_FATAL: Int = 10
        const val MPV_LOG_LEVEL_ERROR: Int = 20
        const val MPV_LOG_LEVEL_WARN: Int = 30
        const val MPV_LOG_LEVEL_INFO: Int = 40
        const val MPV_LOG_LEVEL_V: Int = 50
        const val MPV_LOG_LEVEL_DEBUG: Int = 60
        const val MPV_LOG_LEVEL_TRACE: Int = 70
    }
}
