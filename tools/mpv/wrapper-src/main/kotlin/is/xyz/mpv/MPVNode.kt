package `is`.xyz.mpv

sealed class MPVNode(private val rawJson: String) {
    fun toJson(): String = rawJson
    fun asString(): String? = (this as? StringNode)?.value
    fun asBoolean(): Boolean? = (this as? BooleanNode)?.value
    fun asInt(): Long? = (this as? IntNode)?.value
    fun asDouble(): Double? = (this as? DoubleNode)?.value
    fun asByteArray(): ByteArray? = (this as? ByteArrayNode)?.value
    fun asArray(): Array<MPVNode>? = (this as? ArrayNode)?.value?.toTypedArray()
    fun asMap(): Map<String, MPVNode>? = (this as? MapNode)?.value
    fun keys(): Set<String> = asMap()?.keys ?: emptySet()
    fun size(): Int = asArray()?.size ?: asMap()?.size ?: 0
    operator fun get(index: Int): MPVNode? = asArray()?.getOrNull(index)
    operator fun get(key: String): MPVNode? = asMap()?.get(key)
    fun isEmpty(): Boolean = size() == 0

    object None : MPVNode("null")
    data class StringNode(val value: String) : MPVNode("\"${escape(value)}\"")
    data class BooleanNode(val value: Boolean) : MPVNode(value.toString())
    data class IntNode(val value: Long) : MPVNode(value.toString())
    data class DoubleNode(val value: Double) : MPVNode(value.toString())
    data class ByteArrayNode(val value: ByteArray) : MPVNode("null")
    data class ArrayNode(val value: List<MPVNode>) : MPVNode(value.joinToString(prefix = "[", postfix = "]", separator = ",") { it.toJson() })
    data class MapNode(val value: Map<String, MPVNode>) : MPVNode(
        value.entries.joinToString(prefix = "{", postfix = "}", separator = ",") {
            "\"${escape(it.key)}\":${it.value.toJson()}"
        }
    )
    data class JsonNode(val value: String) : MPVNode(value)

    companion object {
        private fun escape(value: String): String =
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
    }
}
