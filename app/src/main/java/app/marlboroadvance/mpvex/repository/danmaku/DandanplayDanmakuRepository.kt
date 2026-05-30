package app.marlboroadvance.mpvex.repository.danmaku

import android.text.Html
import app.marlboroadvance.mpvex.utils.media.MediaInfoParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

data class DanmakuAnime(
  val bangumiId: Long,
  val title: String,
  val type: String?,
  val typeDescription: String?,
)

data class DanmakuEpisode(
  val episodeId: Long,
  val title: String,
  val number: String?,
)

data class DanmakuComment(
  val time: Float,
  val type: Int,
  val color: Long,
  val text: String,
  val row: Int = 0,
)

data class DanmakuMatchResult(
  val animeTitle: String,
  val episodeTitle: String,
  val episodeId: Long,
)

data class ParsedDanmakuTitle(
  val title: String,
  val season: Int?,
  val episode: Int?,
)

class DandanplayDanmakuRepository(
  private val client: OkHttpClient,
  private val json: Json,
) {
  suspend fun searchAnime(query: String): List<DanmakuAnime> =
    withContext(Dispatchers.IO) {
      val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
      val response = get("$API_SERVER/api/v2/search/anime?keyword=$encoded")
      json.decodeFromString<SearchResponseDto>(response).animes
        .map {
          DanmakuAnime(
            bangumiId = it.bangumiId,
            title = it.animeTitle,
            type = it.type,
            typeDescription = it.typeDescription,
          )
        }
    }

  suspend fun getEpisodes(bangumiId: Long): List<DanmakuEpisode> =
    withContext(Dispatchers.IO) {
      val response = get("$API_SERVER/api/v2/bangumi/$bangumiId")
      json.decodeFromString<BangumiResponseDto>(response).bangumi?.episodes.orEmpty()
        .map {
          DanmakuEpisode(
            episodeId = it.episodeId,
            title = it.episodeTitle.ifBlank { "Episode ${it.episodeNumberText ?: it.episodeId}" },
            number = it.episodeNumberText,
          )
        }
    }

  suspend fun getComments(episodeId: Long): List<DanmakuComment> =
    withContext(Dispatchers.IO) {
      val response = get("$API_SERVER/api/v2/comment/$episodeId?withRelated=true&chConvert=0")
      json.decodeFromString<CommentResponseDto>(response).comments.mapNotNull(::parseComment)
    }

  suspend fun matchDanmaku(fileName: String, filePath: String?): List<DanmakuMatchResult> =
    withContext(Dispatchers.IO) {
      val parsed = parseTitleForDanmaku(fileName)
      val hash = computeFileHash(filePath)
      val body = json.encodeToString(MatchRequest.serializer(), MatchRequest(
        fileName = parsed?.let { buildMatchFileName(it) } ?: fileName,
        fileHash = hash ?: DUMMY_HASH,
        matchMode = "hashAndFileName",
      ))
      val response = post("$API_SERVER/api/v2/match", body)
      val result = json.decodeFromString<MatchResponseDto>(response)
      if (!result.isMatched) return@withContext emptyList()

      result.matches
        .mapNotNull {
          if (it.episodeId <= 0) return@mapNotNull null
          DanmakuMatchResult(
            animeTitle = it.animeTitle,
            episodeTitle = it.episodeTitle,
            episodeId = it.episodeId,
          )
        }
    }

  suspend fun autoMatchDanmaku(
    fileName: String,
    filePath: String?,
  ): DanmakuEpisode? = withContext(Dispatchers.IO) {
    val parsed = parseTitleForDanmaku(fileName)
    if (parsed == null || parsed.episode == null) return@withContext null

    val matchResults = matchDanmaku(fileName, filePath)
    if (matchResults.isNotEmpty()) {
      val best = matchResults.first()
      return@withContext DanmakuEpisode(
        episodeId = best.episodeId,
        title = best.episodeTitle,
        number = parsed.episode.toString(),
      )
    }

    val title = parsed.title
    val encoded = URLEncoder.encode(title, StandardCharsets.UTF_8.name())
    val searchResponse = get("$API_SERVER/api/v2/search/anime?keyword=$encoded")
    val animes = json.decodeFromString<SearchResponseDto>(searchResponse).animes
    if (animes.isEmpty()) return@withContext null

    val bestAnime = pickBestAnime(animes, parsed) ?: return@withContext null

    val bangumiResponse = get("$API_SERVER/api/v2/bangumi/${bestAnime.bangumiId}")
    val episodes = json.decodeFromString<BangumiResponseDto>(bangumiResponse).bangumi?.episodes.orEmpty()
    val episode = episodes.firstOrNull { ep ->
      parseEpisodeNumber(ep.episodeNumberText) == parsed.episode
    }
    if (episode != null) {
      DanmakuEpisode(
        episodeId = episode.episodeId,
        title = episode.episodeTitle.ifBlank { "Episode ${episode.episodeNumberText ?: episode.episodeId}" },
        number = episode.episodeNumberText,
      )
    } else {
      null
    }
  }

  private fun pickBestAnime(
    animes: List<AnimeDto>,
    parsed: ParsedDanmakuTitle,
  ): AnimeDto? {
    val animeType = if (
      parsed.title.contains("OVA", ignoreCase = true) ||
      parsed.title.contains("OAD", ignoreCase = true)
    ) {
      "ova"
    } else {
      "tvseries"
    }
    val candidates = animes.filter { it.type == animeType }.ifEmpty { animes }
    if (candidates.size == 1) return candidates.first()

    var targetTitle = parsed.title
    if ((parsed.season ?: 1) > 1) {
      targetTitle = "${parsed.title} 第${numberToChinese(parsed.season!!)}季"
    } else if (parsed.season == 1 && candidates.any { normalizeAnimeTitle(it.animeTitle).contains(Regex("第一[季部]")) }) {
      targetTitle = "${parsed.title} 第一季"
    }

    val best = candidates
      .map { anime -> anime to jaroWinkler(targetTitle, normalizeAnimeTitle(anime.animeTitle)) }
      .maxByOrNull { it.second }

    return best?.takeIf { it.second >= 0.75f }?.first
  }

  private fun get(url: String): String {
    val request = Request.Builder()
      .url(url)
      .get()
      .addDandanplayHeaders(url)
      .build()

    client.newCall(request).execute().use { response ->
      if (!response.isSuccessful) {
        throw IOException("HTTP ${response.code}")
      }
      return response.body.string()
    }
  }

  private fun post(url: String, body: String): String {
    val mediaType = "application/json".toMediaType()
    val request = Request.Builder()
      .url(url)
      .post(body.toRequestBody(mediaType))
      .addDandanplayHeaders(url)
      .build()

    client.newCall(request).execute().use { response ->
      if (!response.isSuccessful) {
        throw IOException("HTTP ${response.code}")
      }
      return response.body.string()
    }
  }

  private fun Request.Builder.addDandanplayHeaders(url: String): Request.Builder {
    val timestamp = (System.currentTimeMillis() / 1000L).toString()
    val path = url.substringAfter("://").substringAfter("/").substringBefore("?").let { "/$it" }
    val signatureSource = APP_ID + timestamp + path + APP_SECRET
    val signature = Base64.getEncoder().encodeToString(
      MessageDigest.getInstance("SHA-256").digest(signatureSource.toByteArray(StandardCharsets.UTF_8)),
    )

    return header("Accept", "application/json")
      .header("User-Agent", USER_AGENT)
      .header("X-AppId", APP_ID)
      .header("X-Timestamp", timestamp)
      .header("X-Signature", signature)
  }

  private fun parseComment(comment: CommentDto): DanmakuComment? {
    val fields = comment.p?.split(',') ?: return null
    val time = fields.getOrNull(0)?.toFloatOrNull()?.let { it + (comment.shift ?: 0f) } ?: return null
    val type = fields.getOrNull(1)?.toIntOrNull() ?: 1
    if (type !in 1..5) return null
    val color = fields.getOrNull(2)?.toLongOrNull()?.coerceIn(0, 0xFFFFFF) ?: 0xFFFFFF
    val text = sanitizeText(comment.m)
    if (text.isBlank()) return null

    return DanmakuComment(
      time = time,
      type = type,
      color = if (color == 0L) 0xFFFFFF else color,
      text = text,
    )
  }

  @Suppress("DEPRECATION")
  private fun sanitizeText(value: String?): String {
    if (value.isNullOrBlank()) return ""
    val decoded = Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString()
    return decoded
      .replace(Regex("[\\u0000-\\u001F]"), "")
      .replace("\\", "")
      .replace("\"", "")
      .trim()
  }

  companion object {
    fun parseTitleForDanmaku(fileName: String): ParsedDanmakuTitle? {
      val source = fileName
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .replace('\u2009', ' ')
        .replace(Regex("""\.(mkv|mp4|avi|mov|wmv|flv|webm|m4v|mpg|mpeg|m2ts|vob|ogm|rmvb)$""", RegexOption.IGNORE_CASE), "")
        .trim()

      if (source.isBlank()) return null

      val formatterResult = parseWithScriptFormatters(source)
      if (formatterResult != null) return formatterResult

      val parsedMedia = MediaInfoParser.parse(source)
      val title = cleanName(parsedMedia.title)
      if (title.isBlank()) return null

      val hasExplicitSeason = hasExplicitSeason(source)
      return ParsedDanmakuTitle(
        title = title,
        season = parsedMedia.season?.takeIf { hasExplicitSeason },
        episode = parsedMedia.episode,
      )
    }

    fun buildMatchFileName(parsed: ParsedDanmakuTitle): String {
      return if (parsed.season != null && parsed.episode != null) {
        "${parsed.title} S${parsed.season}E${parsed.episode}"
      } else if (parsed.episode != null) {
        "${parsed.title} E${parsed.episode}"
      } else {
        parsed.title
      }
    }

    fun computeFileHash(filePath: String?): String? {
      if (filePath.isNullOrBlank()) return null
      val file = File(filePath)
      if (!file.isFile || file.length() <= 16 * 1024 * 1024) return null
      return try {
        val md = MessageDigest.getInstance("MD5")
        FileInputStream(file).use { fis ->
          val buf = ByteArray(8192)
          var total = 0L
          var read: Int
          while (fis.read(buf).also { read = it } != -1 && total < CHUNK_SIZE) {
            val toRead = minOf(read.toLong(), CHUNK_SIZE - total).toInt()
            md.update(buf, 0, toRead)
            total += toRead
          }
        }
        md.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
      } catch (_: Exception) {
        null
      }
    }

    private fun parseWithScriptFormatters(source: String): ParsedDanmakuTitle? {
      matchRegex(source, Regex("""^(.*?)\s*[_\-\.\s]\s*第\s*(\d+)\s*[季部]+\s*[_\-\.\s]\s*第\s*(\d+[\.vV]?\d*)\s*[话集回]""")) {
        parsed(cleanName(groupValues[1]), groupValues[2].toIntOrNull(), parseEpisodeToken(groupValues[3]))
      }?.let { return it }

      matchRegex(source, Regex("""^(.*?)\s*[_\-\.\s]\s*第([一二三四五六七八九十百千万]+)[季部]+\s*[_\-\.\s]\s*第\s*(\d+[\.vV]?\d*)\s*[话集回]""")) {
        parsed(cleanName(groupValues[1]), chineseToNumber(groupValues[2]), parseEpisodeToken(groupValues[3]))
      }?.let { return it }

      matchRegex(source, Regex("""^(.*?)\s*[_\-\.\s]\s*第\s*(\d+)\s*[季部]+\s*[_\-\.\s]\s*[^\ddD][eEpP]+(\d+[\.vV]?\d*)""")) {
        parsed(cleanName(groupValues[1]), groupValues[2].toIntOrNull(), parseEpisodeToken(groupValues[3]))
      }?.let { return it }

      matchRegex(source, Regex("""^(.*?)\s*[_\-\.\s]\s*第([一二三四五六七八九十百千万]+)[季部]+\s*[_\-\.\s]\s*[^\ddD][eEpP]+(\d+[\.vV]?\d*)""")) {
        parsed(cleanName(groupValues[1]), chineseToNumber(groupValues[2]), parseEpisodeToken(groupValues[3]))
      }?.let { return it }

      matchRegex(source, Regex("""^(.*?)\s*[_\.\s]\s*(\d{4})\s*[_\.\s]\s*[sS](\d+)[\.\-\s:]?[eE](\d+[\.vV]?\d*)""")) {
        parsed("${cleanName(groupValues[1])} (${groupValues[2]})", groupValues[3].toIntOrNull(), parseEpisodeToken(groupValues[4]))
      }?.let { return it }

      matchRegex(source, Regex("""^(.*?)\s*[_\.\s]\s*(\d{4})\s*[_\.\s]\s*[^\ddD][eEpP]+(\d+[\.vV]?\d*)""")) {
        parsed("${cleanName(groupValues[1])} (${groupValues[2]})", null, parseEpisodeToken(groupValues[3]))
      }?.let { return it }

      matchRegex(source, Regex("""^(.*?)\s*[_\-\.\s]\s*[sS](\d+)[\.\-\s:]?[eE](\d+[\.vV]?\d*)\s*[_\.\s]\s*(\d{4})[^\dhHxXvVpPkKxXbBfF]""")) {
        parsed("${cleanName(groupValues[1])} (${groupValues[4]})", groupValues[2].toIntOrNull(), parseEpisodeToken(groupValues[3]))
      }?.let { return it }

      matchRegex(source, Regex("""^(.*?)\s*[_\-\.\s]\s*[sS](\d+)[\.\-\s:]?[eE](\d+[\.vV]?\d*)""")) {
        parsed(cleanName(groupValues[1]), groupValues[2].toIntOrNull(), parseEpisodeToken(groupValues[3]))
      }?.let { return it }

      matchRegex(source, Regex("""^(.*?)\s*[^\ddD][eEpP]+(\d+[\.vV]?\d*)[_\.\s]\s*(\d{4})[^\dhHxXvVpPkKxXbBfF]""")) {
        parsed("${cleanName(groupValues[1])} (${groupValues[3]})", null, parseEpisodeToken(groupValues[2]))
      }?.let { return it }

      matchRegex(source, Regex("""^(.*?)\s*[^\ddD][eEpP]+(\d+[\.vV]?\d*)""")) {
        parsed(cleanName(groupValues[1]), null, parseEpisodeToken(groupValues[2]))
      }?.let { return it }

      matchRegex(source, Regex("""^(.*?)\s*第\s*(\d+[\.vV]?\d*)\s*[话集回]""")) {
        parsed(cleanName(groupValues[1]), null, parseEpisodeToken(groupValues[2]))
      }?.let { return it }

      matchRegex(source, Regex("""^(.*?)\s*\[(\d+[\.vV]?\d*)\]""")) {
        parsed(cleanName(groupValues[1]), null, parseEpisodeToken(groupValues[2]))
      }?.let { return it }

      matchRegex(source, Regex("""^(.*?)\s*\[(\d+[\.vV]?\d*)\([A-Za-z]+\)\]""")) {
        parsed(cleanName(groupValues[1]), null, parseEpisodeToken(groupValues[2]))
      }?.let { return it }

      matchRegex(source, Regex("""^(.*?)\s*[\-#]\s*(\d+\.?\d*)\s*""")) {
        parsed(cleanName(groupValues[1]), null, parseEpisodeToken(groupValues[2]))
      }?.let { return it }

      matchRegex(source, Regex("""^(.*?)\s*[_\-\.\s]\s*(\d?\d)[xX](\d{1,4})[^\dhHxXvVpPkKxXbBfF]""")) {
        parsed(cleanName(groupValues[1]), groupValues[2].toIntOrNull(), parseEpisodeToken(groupValues[3]))
      }?.let { return it }

      return null
    }

    private inline fun matchRegex(
      source: String,
      regex: Regex,
      block: MatchResult.() -> ParsedDanmakuTitle?,
    ): ParsedDanmakuTitle? = regex.find(source)?.block()

    private fun parsed(title: String, season: Int?, episode: Int?): ParsedDanmakuTitle? {
      if (title.isBlank()) return null
      if (episode == null) return null
      return ParsedDanmakuTitle(title = title, season = season, episode = episode)
    }

    private fun cleanName(name: String): String =
      name
        .replace(Regex("""^\[.*?]"""), " ")
        .replace(Regex("""^\(.*?\)"""), " ")
        .replace(Regex("""[_\.\[\]]"""), " ")
        .replace(Regex("""第\s*\d+\s*[季部]"""), " ")
        .replace(Regex("""第[一二三四五六七八九十百千万]+[季部]"""), " ")
        .replace(Regex("""\b(19|20)\d{2}\b"""), " ")
        .replace(Regex("""\b\d{3,4}[pPiI]\b"""), " ")
        .replace(Regex("""\b\d+\.?\d*\s*[KkMm]bps\b"""), " ")
        .replace(Regex("""\b\d+\.?\d*\s*[MmGg][Bb]\b"""), " ")
        .replace(Regex("""\b(?:DDP?|AAC|DD\+?)?\.?([257])\.([01])\b""", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("""\b[Hh]\.?26[45]\b"""), " ")
        .replace(Regex("""\b(?:DTS[-.]?HD(?:[-.]?MA)?|TrueHD|DD\+?|DDP)\b""", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .trimEnd('!', '@', '#', '.', '?', '+', '-', '%', '&', '*', '_', '=', ',', '/', '~', '`')
        .trim()

    private fun parseEpisodeToken(value: String): Int? {
      val number = value
        .replace(Regex("""[vV]\d+$"""), "")
        .substringBefore('.')
        .toIntOrNull()
        ?: return null
      return number.takeUnless { it in setOf(480, 720, 1080, 2160) || it in 1900..2100 }
    }

    private fun parseEpisodeNumber(value: String?): Int? =
      value
        ?.substringBefore('.')
        ?.toIntOrNull()

    private fun hasExplicitSeason(source: String): Boolean =
      Regex("""[sS]\d{1,2}[\.\-\s:]?[eE]\d+|\b\d{1,2}[xX]\d{1,4}\b|第\s*\d+\s*[季部]|第[一二三四五六七八九十百千万]+[季部]|\b[Ss]eason\s*\d+\b""")
        .containsMatchIn(source)

    private fun normalizeAnimeTitle(title: String): String =
      title
        .trim()
        .replace(Regex("""\s*\(.*?\)\s*$"""), "")
        .replace(Regex("""\s*【.*?】.*$"""), "")
        .replace(Regex("""\s*\[.*?]\s*$"""), "")
        .trim()

    private fun chineseToNumber(value: String): Int? {
      val numberMap = mapOf(
        '零' to 0,
        '一' to 1,
        '二' to 2,
        '三' to 3,
        '四' to 4,
        '五' to 5,
        '六' to 6,
        '七' to 7,
        '八' to 8,
        '九' to 9,
      )
      val unitMap = mapOf('十' to 10, '百' to 100, '千' to 1000, '万' to 10000)
      var total = 0
      var section = 0
      var number = 0

      value.forEach { char ->
        when {
          numberMap.containsKey(char) -> number = numberMap.getValue(char)
          unitMap.containsKey(char) -> {
            val unit = unitMap.getValue(char)
            val current = if (number == 0) 1 else number
            section += current * unit
            number = 0
          }
          else -> return null
        }
      }

      total += section + number
      return total.takeIf { it > 0 }
    }

    private fun jaroWinkler(s1: String, s2: String): Float {
      if (s1 == s2) return 1f
      if (s1.isEmpty() || s2.isEmpty()) return 0f

      val matchDistance = ((maxOf(s1.length, s2.length) / 2) - 1).coerceAtLeast(0)
      val s1Matches = BooleanArray(s1.length)
      val s2Matches = BooleanArray(s2.length)
      var matches = 0
      var transpositions = 0

      for (i in s1.indices) {
        val start = (i - matchDistance).coerceAtLeast(0)
        val end = (i + matchDistance + 1).coerceAtMost(s2.length)
        for (j in start until end) {
          if (s2Matches[j] || s1[i] != s2[j]) continue
          s1Matches[i] = true
          s2Matches[j] = true
          matches++
          break
        }
      }

      if (matches == 0) return 0f

      var k = 0
      for (i in s1.indices) {
        if (!s1Matches[i]) continue
        while (!s2Matches[k]) k++
        if (s1[i] != s2[k]) transpositions++
        k++
      }
      transpositions /= 2

      val jaro = ((matches.toFloat() / s1.length) + (matches.toFloat() / s2.length) + ((matches - transpositions).toFloat() / matches)) / 3f
      var prefix = 0
      for (i in 0 until minOf(4, minOf(s1.length, s2.length))) {
        if (s1[i] == s2[i]) prefix++ else break
      }
      return jaro + prefix * 0.1f * (1f - jaro)
    }

    private fun numberToChinese(num: Int): String {
      val digits = listOf("零", "一", "二", "三", "四", "五", "六", "七", "八", "九")
      if (num < 10) return digits[num]
      if (num < 100) {
        val tens = num / 10
        val ones = num % 10
        return if (tens == 1) "十${if (ones > 0) digits[ones] else ""}"
        else "${digits[tens]}十${if (ones > 0) digits[ones] else ""}"
      }
      return num.toString()
    }

    private const val CHUNK_SIZE = 16L * 1024L * 1024L
    const val API_SERVER = "https://api.dandanplay.net"
    const val USER_AGENT = "mpvEx/1.0"
    const val APP_ID = "gz2wnihj9d"
    const val APP_SECRET = "qo9N3YVARNZc7MWHce7v92q1SUe5uYJ8"
    const val DUMMY_HASH = "a1b2c3d4e5f67890abcd1234ef567890"
  }
}

@Serializable
private data class SearchResponseDto(
  val animes: List<AnimeDto> = emptyList(),
)

@Serializable
private data class AnimeDto(
  val bangumiId: Long,
  val animeTitle: String,
  val type: String? = null,
  val typeDescription: String? = null,
)

@Serializable
private data class BangumiResponseDto(
  val bangumi: BangumiDto? = null,
)

@Serializable
private data class BangumiDto(
  val episodes: List<EpisodeDto> = emptyList(),
)

@Serializable
private data class EpisodeDto(
  val episodeId: Long,
  val episodeTitle: String = "",
  val episodeNumber: JsonElement? = null,
) {
  val episodeNumberText: String?
    get() = episodeNumber?.jsonPrimitive?.contentOrNull
}

@Serializable
private data class CommentResponseDto(
  val count: Int = 0,
  val comments: List<CommentDto> = emptyList(),
)

@Serializable
private data class CommentDto(
  val p: String? = null,
  val m: String? = null,
  @SerialName("shift") val shift: Float? = null,
)

@Serializable
private data class MatchRequest(
  val fileName: String,
  val fileHash: String,
  val matchMode: String,
)

@Serializable
private data class MatchResponseDto(
  val isMatched: Boolean = false,
  val matches: List<MatchItemDto> = emptyList(),
)

@Serializable
private data class MatchItemDto(
  val animeTitle: String = "",
  val episodeTitle: String = "",
  val episodeId: Long = 0,
)
