package app.windusth.mpvdanmuku.repository.danmaku

import android.text.Html
import android.net.Uri
import android.util.Log
import app.windusth.mpvdanmuku.preferences.DandanplayOAuthStore
import app.windusth.mpvdanmuku.preferences.DanmakuPreferences
import app.windusth.mpvdanmuku.utils.media.MediaInfoParser
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
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
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
  val repeatCount: Int = 1,
  val isSelf: Boolean = false,
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

data class SendCommentResult(
  val success: Boolean,
  val cid: Long = 0,
  val errorCode: Int = 0,
  val errorMessage: String? = null,
)

data class DandanplayOAuthResult(
  val success: Boolean,
  val message: String,
)

private data class AnimeEpisodeSelection(
  val anime: AnimeDto,
  val episodeNumber: Int?,
)

private data class LlmParseResult(
  val titles: List<String>,
  val season: Int?,
  val episode: Int?,
)

private data class DandanplayCredentials(
  val appId: String,
  val appSecret: String,
  val isUserConfigured: Boolean,
)

class DandanplayDanmakuRepository(
  private val client: OkHttpClient,
  private val json: Json,
  private val preferences: DanmakuPreferences,
  private val oauthStore: DandanplayOAuthStore,
) {
  fun createOAuthAuthorizationUrl(): String {
    val clientId = preferences.dandanplayAppId.get().trim()
    if (clientId.isBlank()) {
      throw IOException("Configure dandanplay AppId before authorization")
    }

    val redirectUri = preferences.dandanplayOAuthRedirectUri.get().trim()
      .ifBlank { DEFAULT_OAUTH_REDIRECT_URI }
    val scope = preferences.dandanplayOAuthScope.get().trim()
      .ifBlank { DEFAULT_OAUTH_SCOPE }
    val state = randomBase64Url(24)
    val verifier = randomBase64Url(48)
    val challenge = sha256Base64Url(verifier)

    oauthStore.savePendingAuthorization(state, verifier)

    return Uri.parse("$API_SERVER/api/v2/oauth/login")
      .buildUpon()
      .appendQueryParameter("client_id", clientId)
      .appendQueryParameter("redirect_uri", redirectUri)
      .appendQueryParameter("response_type", "code")
      .appendQueryParameter("scope", scope)
      .appendQueryParameter("state", state)
      .appendQueryParameter("code_challenge", challenge)
      .appendQueryParameter("code_challenge_method", "S256")
      .build()
      .toString()
  }

  suspend fun completeOAuthAuthorization(uri: Uri): DandanplayOAuthResult =
    withContext(Dispatchers.IO) {
      val error = uri.getQueryParameter("error")
      if (!error.isNullOrBlank()) {
        val description = uri.getQueryParameter("error_description")
        return@withContext DandanplayOAuthResult(
          success = false,
          message = listOfNotNull(error, description).joinToString(": "),
        )
      }

      val code = uri.getQueryParameter("code")
        ?: return@withContext DandanplayOAuthResult(false, "OAuth callback did not include code")
      val state = uri.getQueryParameter("state")
        ?: return@withContext DandanplayOAuthResult(false, "OAuth callback did not include state")
      val verifier = oauthStore.pendingCodeVerifierFor(state)
        ?: return@withContext DandanplayOAuthResult(false, "OAuth state mismatch; start authorization again")

      val token = exchangeOAuthToken(
        OAuthTokenRequestDto(
          clientId = configuredOAuthClientId(),
          clientSecret = preferences.dandanplayAppSecret.get().trim().takeIf { it.isNotBlank() },
          code = code,
          redirectUri = preferences.dandanplayOAuthRedirectUri.get().trim().ifBlank { DEFAULT_OAUTH_REDIRECT_URI },
          grantType = "authorization_code",
          codeVerifier = verifier,
        ),
      )

      oauthStore.saveTokens(
        accessToken = token.accessToken,
        refreshToken = token.refreshToken,
        expiresAtMillis = tokenExpiresAt(token.expiresIn),
        scope = token.scope,
      )

      DandanplayOAuthResult(true, "dandanplay authorization complete")
    }

  suspend fun refreshOAuthAccessTokenIfNeeded(force: Boolean = false): String? =
    withContext(Dispatchers.IO) {
      val state = oauthStore.current
      if (!force && state.accessToken != null && !isTokenExpiringSoon(state.expiresAtMillis)) {
        return@withContext state.accessToken
      }

      val refreshToken = state.refreshToken
      if (refreshToken.isNullOrBlank()) {
        return@withContext state.accessToken?.takeIf { it.isNotBlank() }
      }

      val token = exchangeOAuthToken(
        OAuthTokenRequestDto(
          clientId = configuredOAuthClientId(),
          clientSecret = preferences.dandanplayAppSecret.get().trim().takeIf { it.isNotBlank() },
          refreshToken = refreshToken,
          grantType = "refresh_token",
        ),
      )

      oauthStore.saveTokens(
        accessToken = token.accessToken,
        refreshToken = token.refreshToken ?: refreshToken,
        expiresAtMillis = tokenExpiresAt(token.expiresIn),
        scope = token.scope ?: state.scope,
      )
      token.accessToken
    }

  suspend fun sendCommentAuthorized(
    episodeId: Long,
    time: Float,
    mode: Int,
    color: Int,
    comment: String,
  ): SendCommentResult = withContext(Dispatchers.IO) {
    val token = refreshOAuthAccessTokenIfNeeded()
      ?: return@withContext SendCommentResult(
        success = false,
        errorCode = 401,
        errorMessage = "Authorize dandanplay before sending danmaku",
      )

    val result = if (preferences.dandanplayCommentProxyUrl.get().isNotBlank()) {
      sendCommentViaProxy(episodeId, time, mode, color, comment, token)
    } else {
      sendComment(episodeId, time, mode, color, comment, token)
    }

    if (result.errorCode != 401) return@withContext result

    val refreshedToken = runCatching { refreshOAuthAccessTokenIfNeeded(force = true) }.getOrNull()
      ?: return@withContext result
    val retry = if (preferences.dandanplayCommentProxyUrl.get().isNotBlank()) {
      sendCommentViaProxy(episodeId, time, mode, color, comment, refreshedToken)
    } else {
      sendComment(episodeId, time, mode, color, comment, refreshedToken)
    }
    if (retry.errorCode == 401) {
      oauthStore.logout()
    }
    retry
  }

  suspend fun searchAnime(query: String): List<DanmakuAnime> =
    withContext(Dispatchers.IO) {
      val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
      val response = get("$API_SERVER/api/v2/search/anime?keyword=$encoded")
      json.decodeFromString<SearchResponseDto>(response).animes
        .mapNotNull {
          val bangumiId = it.bangumiIdLong ?: return@mapNotNull null
          DanmakuAnime(
            bangumiId = bangumiId,
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
    // --- LLM-based fast path ---
    val llmResult = parseTitleWithLlm(fileName, filePath)
    if (llmResult != null) {
      val parsed = ParsedDanmakuTitle(
        title = llmResult.titles.firstOrNull() ?: "",
        season = llmResult.season,
        episode = llmResult.episode,
      )

      // Try dandanplay /match with the primary parsed title
      if (parsed.title.isNotBlank()) {
        val matchResults = matchDanmaku(buildMatchFileName(parsed), filePath)
        if (matchResults.isNotEmpty()) {
          val best = matchResults.first()
          return@withContext DanmakuEpisode(
            episodeId = best.episodeId,
            title = best.episodeTitle,
            number = llmResult.episode?.toString(),
          )
        }
      }

      // Try searching each title (primary + alternatives) on dandanplay
      for (searchTitle in llmResult.titles) {
        val encoded = URLEncoder.encode(searchTitle, StandardCharsets.UTF_8.name())
        val searchResponse = get("$API_SERVER/api/v2/search/anime?keyword=$encoded")
        val animes = json.decodeFromString<SearchResponseDto>(searchResponse).animes
        if (animes.isEmpty()) continue

        val selection = pickAnimeEpisode(animes, parsed)
        if (selection != null) {
          val bestAnime = selection.anime
          val targetEpisode = selection.episodeNumber ?: llmResult.episode
          val bangumiId = bestAnime.bangumiIdLong ?: continue

          val bangumiResponse = get("$API_SERVER/api/v2/bangumi/$bangumiId")
          val episodes = json.decodeFromString<BangumiResponseDto>(bangumiResponse).bangumi?.episodes.orEmpty()
          val episode = if (targetEpisode != null) {
            episodes.firstOrNull { ep -> parseEpisodeNumber(ep.episodeNumberText) == targetEpisode }
          } else if (bestAnime.type == "movie" || episodes.size == 1) {
            episodes.firstOrNull()
          } else {
            null
          }
          if (episode != null) {
            return@withContext DanmakuEpisode(
              episodeId = episode.episodeId,
              title = episode.episodeTitle.ifBlank { "Episode ${episode.episodeNumberText ?: episode.episodeId}" },
              number = episode.episodeNumberText,
            )
          }
        }
      }
    }

    // --- Regex-based fallback ---
    for (matchName in matchCandidateNames(fileName, filePath)) {
      val parsed = parseTitleForDanmaku(matchName)
      if (parsed == null) continue

      val matchResults = matchDanmaku(matchName, filePath)
      if (matchResults.isNotEmpty()) {
        val best = matchResults.first()
        return@withContext DanmakuEpisode(
          episodeId = best.episodeId,
          title = best.episodeTitle,
          number = parsed.episode?.toString(),
        )
      }

      val title = parsed.title
      val encoded = URLEncoder.encode(title, StandardCharsets.UTF_8.name())
      val searchResponse = get("$API_SERVER/api/v2/search/anime?keyword=$encoded")
      val animes = json.decodeFromString<SearchResponseDto>(searchResponse).animes
      if (animes.isEmpty()) continue

      val selection = pickAnimeEpisode(animes, parsed) ?: continue
      val bestAnime = selection.anime
      val targetEpisode = selection.episodeNumber ?: parsed.episode
      val bangumiId = bestAnime.bangumiIdLong ?: continue

      val bangumiResponse = get("$API_SERVER/api/v2/bangumi/$bangumiId")
      val episodes = json.decodeFromString<BangumiResponseDto>(bangumiResponse).bangumi?.episodes.orEmpty()
      val episode = if (targetEpisode != null) {
        episodes.firstOrNull { ep ->
          parseEpisodeNumber(ep.episodeNumberText) == targetEpisode
        }
      } else if (bestAnime.type == "movie" || episodes.size == 1) {
        episodes.firstOrNull()
      } else {
        null
      }
      if (episode != null) {
        return@withContext DanmakuEpisode(
          episodeId = episode.episodeId,
          title = episode.episodeTitle.ifBlank { "Episode ${episode.episodeNumberText ?: episode.episodeId}" },
          number = episode.episodeNumberText,
        )
      }
    }

    null
  }

  suspend fun sendComment(
    episodeId: Long,
    time: Float,
    mode: Int,
    color: Int,
    comment: String,
    token: String,
  ): SendCommentResult = withContext(Dispatchers.IO) {
    val credentials = configuredDandanplayCredentials()
      ?: return@withContext SendCommentResult(
        success = false,
        errorCode = 403,
        errorMessage = "Direct dandanplay sending requires authorized AppId/AppSecret or a comment proxy URL",
      )
    val body = json.encodeToString(SendCommentRequestDto.serializer(), SendCommentRequestDto(
      time = time.toDouble(),
      mode = mode,
      color = color,
      comment = comment.trim().take(100),
    ))

    val mediaType = "application/json".toMediaType()
    val url = "$API_SERVER/api/v2/comment/$episodeId"
    val request = Request.Builder()
      .url(url)
      .post(body.toRequestBody(mediaType))
      .addDandanplayHeaders(url, credentials)
      .header("Authorization", "Bearer $token")
      .build()

    client.newCall(request).execute().use { response ->
      val responseBody = response.body.string()
      if (response.isSuccessful && responseBody.isNotBlank()) {
        val result = json.decodeFromString<SendCommentResponseDto>(responseBody)
        SendCommentResult(
          success = result.success,
          cid = result.cid,
          errorCode = result.errorCode,
          errorMessage = explainDandanplayAuthError(
            message = result.errorMessage,
            errorCode = result.errorCode,
            credentials = credentials,
          ),
        )
      } else {
        val errorMessage = response.header("X-Error-Message")
          ?: responseBody.takeIf { it.isNotBlank() }
          ?: "HTTP ${response.code}"
        SendCommentResult(
          success = false,
          errorCode = response.code,
          errorMessage = explainDandanplayAuthError(
            message = errorMessage,
            errorCode = response.code,
            credentials = credentials,
          ),
        )
      }
    }
  }

  private fun sendCommentViaProxy(
    episodeId: Long,
    time: Float,
    mode: Int,
    color: Int,
    comment: String,
    token: String,
  ): SendCommentResult {
    val proxyUrl = preferences.dandanplayCommentProxyUrl.get().trim()
    if (proxyUrl.isBlank()) {
      return SendCommentResult(
        success = false,
        errorCode = 400,
        errorMessage = "Comment proxy URL is empty",
      )
    }

    val body = json.encodeToString(
      ProxySendCommentRequestDto.serializer(),
      ProxySendCommentRequestDto(
        episodeId = episodeId,
        time = time.toDouble(),
        mode = mode,
        color = color,
        comment = comment.trim().take(100),
      ),
    )

    val mediaType = "application/json".toMediaType()
    val request = Request.Builder()
      .url(proxyUrl)
      .post(body.toRequestBody(mediaType))
      .header("Accept", "application/json")
      .header("Content-Type", "application/json")
      .header("User-Agent", USER_AGENT)
      .header("Authorization", "Bearer $token")
      .build()

    client.newCall(request).execute().use { response ->
      val responseBody = response.body.string()
      if (response.isSuccessful && responseBody.isNotBlank()) {
        val result = json.decodeFromString<SendCommentResponseDto>(responseBody)
        return SendCommentResult(
          success = result.success,
          cid = result.cid,
          errorCode = result.errorCode,
          errorMessage = result.errorMessage,
        )
      }
      return SendCommentResult(
        success = false,
        errorCode = response.code,
        errorMessage = responseBody.takeIf { it.isNotBlank() } ?: "HTTP ${response.code}",
      )
    }
  }

  private fun exchangeOAuthToken(requestDto: OAuthTokenRequestDto): OAuthTokenResponseDto {
    val endpoint = if (requestDto.grantType == "refresh_token") {
      "$API_SERVER/api/v2/oauth/refresh"
    } else {
      "$API_SERVER/api/v2/oauth/token"
    }
    val request = Request.Builder()
      .url(endpoint)
      .post(requestDto.toFormBody())
      .header("Accept", "application/json")
      .header("User-Agent", USER_AGENT)
      .build()

    client.newCall(request).execute().use { response ->
      val responseBody = response.body.string()
      if (!response.isSuccessful) {
        throw IOException(responseBody.takeIf { it.isNotBlank() } ?: "OAuth HTTP ${response.code}")
      }
      val token = json.decodeFromString<OAuthTokenResponseDto>(responseBody)
      if (token.accessToken.isBlank()) {
        throw IOException("OAuth token response did not include access_token")
      }
      return token
    }
  }

  private fun OAuthTokenRequestDto.toFormBody(): FormBody {
    val builder = FormBody.Builder()
      .add("client_id", clientId)
      .add("grant_type", grantType)
    clientSecret?.takeIf { it.isNotBlank() }?.let { builder.add("client_secret", it) }
    code?.takeIf { it.isNotBlank() }?.let { builder.add("code", it) }
    redirectUri?.takeIf { it.isNotBlank() }?.let { builder.add("redirect_uri", it) }
    codeVerifier?.takeIf { it.isNotBlank() }?.let { builder.add("code_verifier", it) }
    refreshToken?.takeIf { it.isNotBlank() }?.let { builder.add("refresh_token", it) }
    return builder.build()
  }

  private val llmClient: OkHttpClient by lazy {
    client.newBuilder()
      .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
      .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
      .writeTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
      .build()
  }

  private suspend fun parseTitleWithLlm(
    fileName: String,
    filePath: String?,
  ): LlmParseResult? {
    val baseUrl = preferences.llmApiBaseUrl.get().trimEnd('/')
    val apiKey = preferences.llmApiKey.get()
    val model = preferences.llmModel.get()

    if (baseUrl.isBlank() || apiKey.isBlank()) return null

    return withContext(Dispatchers.IO) {
      try {
        val systemPrompt = """
          You are a filename parser for anime video files. Extract the anime title, season number, and episode number from the given filename and path.
          Rules:
          - Return ONLY valid JSON with exactly these fields: "title" (string), "season" (integer or null), "episode" (integer or null), "alternativeTitles" (array of strings)
          - "title" should be the normalized anime title in the most recognizable form (Japanese romaji, English, or the original language title)
          - "alternativeTitles" should include other names the anime might be known as, such as: the original Japanese title, Chinese title, English title, or common abbreviations. Include up to 5 alternatives.
          - Remove all release group names, codec info, resolution, and technical metadata from the title
          - Use parent folder names as context when the filename only contains an episode number
          - If no season is specified, set "season" to null
          - If no episode number can be determined, set "episode" to null
          - Do not include any explanation, only the JSON object
        """.trimIndent()
        val decodedPath = filePath?.let(::decodePathSegment)?.takeIf { it.isNotBlank() }
        val userPrompt = buildString {
          append("filename: ")
          append(fileName)
          if (decodedPath != null) {
            append('\n')
            append("path: ")
            append(decodedPath)
          }
        }

        val requestBody = json.encodeToString(
          LlmChatRequest.serializer(),
          LlmChatRequest(
            model = model.ifBlank { "gpt-4o-mini" },
            messages = listOf(
              LlmMessage(role = "system", content = systemPrompt),
              LlmMessage(role = "user", content = userPrompt),
            ),
            temperature = 0.1,
          ),
        )

        val mediaType = "application/json".toMediaType()
        val request = Request.Builder()
          .url("$baseUrl/chat/completions")
          .post(requestBody.toRequestBody(mediaType))
          .header("Authorization", "Bearer $apiKey")
          .header("Content-Type", "application/json")
          .build()

        llmClient.newCall(request).execute().use { response ->
          if (!response.isSuccessful) {
            Log.w(TAG, "LLM API returned HTTP ${response.code}")
            return@withContext null
          }
          val responseBody = response.body.string()
          val chatResponse = json.decodeFromString<LlmChatResponse>(responseBody)
          val content = chatResponse.choices?.firstOrNull()?.message?.content ?: return@withContext null

          val cleanedContent = extractJsonObject(content) ?: return@withContext null

          val parsed = json.decodeFromString<LlmTitleParseResult>(cleanedContent)
          if (parsed.title.isBlank()) return@withContext null

          val allTitles = (listOf(parsed.title) + parsed.alternativeTitles)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
          Log.d(TAG, "LLM parsed \"$fileName\" → titles=$allTitles, season=${parsed.season}, episode=${parsed.episode}")
          LlmParseResult(
            titles = allTitles,
            season = parsed.season,
            episode = parsed.episode,
          )
        }
      } catch (e: Exception) {
        Log.w(TAG, "LLM parsing failed for \"$fileName\": ${e.message}")
        null
      }
    }
  }

  private fun extractJsonObject(content: String): String? {
    val cleaned = content.trim()
      .removePrefix("```json")
      .removePrefix("```")
      .removeSuffix("```")
      .trim()
    val start = cleaned.indexOf('{')
    if (start < 0) return null

    var depth = 0
    var inString = false
    var escaped = false
    for (index in start until cleaned.length) {
      val char = cleaned[index]
      if (escaped) {
        escaped = false
        continue
      }
      when {
        char == '\\' && inString -> escaped = true
        char == '"' -> inString = !inString
        !inString && char == '{' -> depth++
        !inString && char == '}' -> {
          depth--
          if (depth == 0) return cleaned.substring(start, index + 1)
        }
      }
    }

    return null
  }

  private fun matchCandidateNames(fileName: String, filePath: String?): List<String> {
    val decodedPath = filePath?.let(::decodePathSegment)
    val pathName = decodedPath
      ?.substringAfterLast('/')
      ?.substringAfterLast('\\')
      ?.takeIf { it.isNotBlank() }

    val names = mutableListOf<String>()
    names += listOf(fileName, pathName)
      .mapNotNull { it?.takeIf(String::isNotBlank) }
      .distinct()

    val parsedFile = names.firstNotNullOfOrNull { parseTitleForDanmaku(it) }
    val episode = parsedFile?.episode
    if (decodedPath != null) {
      parentCandidateTitles(decodedPath).forEach { parentTitle ->
        if (episode != null) {
          names += buildMatchFileName(
            ParsedDanmakuTitle(
              title = parentTitle,
              season = parsedFile.season,
              episode = episode,
            ),
          )
        } else {
          names += parentTitle
        }
      }
    }

    return names.distinct()
  }

  private fun decodePathSegment(value: String): String =
    runCatching {
      URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }.getOrDefault(value)

  private fun parentCandidateTitles(filePath: String): List<String> {
    val segments = filePath
      .substringBefore('?')
      .replace('\\', '/')
      .split('/')
      .filter { it.isNotBlank() }
    if (segments.size < 2) return emptyList()

    return segments
      .dropLast(1)
      .asReversed()
      .asSequence()
      .filterNot(::isGenericDanmakuFolder)
      .map { cleanCandidateTitle(it) }
      .filter { it.isNotBlank() }
      .filterNot(::isGenericDanmakuFolder)
      .distinct()
      .take(3)
      .toList()
  }

  private fun isGenericDanmakuFolder(value: String): Boolean {
    val normalized = value.trim().lowercase()
    return normalized.matches(Regex("""(?:season|seasons)\s*\d+""")) ||
      normalized in setOf("special", "specials", "sp", "sps", "ova", "ovas", "oad", "oads", "op", "ed", "ncop", "nced", "ncop&nced")
  }

  private fun pickBestAnime(
    animes: List<AnimeDto>,
    parsed: ParsedDanmakuTitle,
  ): AnimeDto? {
    val candidates = preferredAnimeCandidates(animes, parsed)
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

  private fun pickAnimeEpisode(
    animes: List<AnimeDto>,
    parsed: ParsedDanmakuTitle,
  ): AnimeEpisodeSelection? {
    val candidates = preferredAnimeCandidates(animes, parsed)
    val seasonCandidate = parsed.season
      ?.takeIf { it > 0 }
      ?.let { season -> candidates.getOrNull(season - 1) }
    if (seasonCandidate != null) {
      return AnimeEpisodeSelection(seasonCandidate, parsed.episode)
    }

    // Try best title match first
    val bestMatch = pickBestAnime(animes, parsed)
    if (bestMatch != null) {
      val episodeFits = parsed.episode == null ||
        bestMatch.episodeCount == null ||
        parsed.episode <= bestMatch.episodeCount
      if (episodeFits) {
        return AnimeEpisodeSelection(bestMatch, parsed.episode)
      }
      // Episode number exceeds this season's count — might be continuous numbering across seasons
      Log.d(TAG, "Episode ${parsed.episode} exceeds ${bestMatch.animeTitle} count ${bestMatch.episodeCount}, trying absolute mapping")
    }

    // Try mapping absolute episode number across multiple seasons
    pickByAbsoluteEpisode(candidates, parsed.episode)?.let {
      return it
    }

    // Fall back to best match anyway (even if episode count seems wrong)
    if (bestMatch != null) {
      return AnimeEpisodeSelection(bestMatch, parsed.episode)
    }

    return candidates.singleOrNull()?.let {
      AnimeEpisodeSelection(it, parsed.episode)
    }
  }

  private fun preferredAnimeCandidates(
    animes: List<AnimeDto>,
    parsed: ParsedDanmakuTitle,
  ): List<AnimeDto> {
    val animeType = if (
      parsed.title.contains("OVA", ignoreCase = true) ||
      parsed.title.contains("OAD", ignoreCase = true)
    ) {
      "ova"
    } else {
      "tvseries"
    }
    return animes.filter { it.type == animeType }.ifEmpty { animes }
  }

  private fun pickByAbsoluteEpisode(
    candidates: List<AnimeDto>,
    episode: Int?,
  ): AnimeEpisodeSelection? {
    if (episode == null || candidates.size <= 1) return null

    var remainingEpisode = episode
    candidates.forEach { anime ->
      val episodeCount = anime.episodeCount ?: return@forEach
      if (remainingEpisode <= episodeCount) {
        return AnimeEpisodeSelection(anime, remainingEpisode)
      }
      remainingEpisode -= episodeCount
    }

    return null
  }

  private fun get(url: String): String {
    val request = Request.Builder()
      .url(url)
      .get()
      .addDandanplayHeaders(url, dandanplayCredentials())
      .build()

    client.newCall(request).execute().use { response ->
      if (!response.isSuccessful) {
        throw IOException("HTTP ${response.code}")
      }
      return response.body.string()
    }
  }

  private fun post(
    url: String,
    body: String,
    credentials: DandanplayCredentials = dandanplayCredentials(),
  ): String {
    val mediaType = "application/json".toMediaType()
    val request = Request.Builder()
      .url(url)
      .post(body.toRequestBody(mediaType))
      .addDandanplayHeaders(url, credentials)
      .build()

    client.newCall(request).execute().use { response ->
      if (!response.isSuccessful) {
        throw IOException("HTTP ${response.code}")
      }
      return response.body.string()
    }
  }

  private fun Request.Builder.addDandanplayHeaders(
    url: String,
    credentials: DandanplayCredentials,
  ): Request.Builder {
    val timestamp = (System.currentTimeMillis() / 1000L).toString()
    val path = url.substringAfter("://").substringAfter("/").substringBefore("?").let { "/$it" }
    val signatureSource = credentials.appId + timestamp + path + credentials.appSecret
    val signature = Base64.getEncoder().encodeToString(
      MessageDigest.getInstance("SHA-256").digest(signatureSource.toByteArray(StandardCharsets.UTF_8)),
    )

    return header("Accept", "application/json")
      .header("User-Agent", USER_AGENT)
      .header("X-AppId", credentials.appId)
      .header("X-Timestamp", timestamp)
      .header("X-Signature", signature)
  }

  private fun dandanplayCredentials(): DandanplayCredentials {
    return configuredDandanplayCredentials()
      ?: DandanplayCredentials(DEFAULT_APP_ID, DEFAULT_APP_SECRET, isUserConfigured = false)
  }

  private fun configuredDandanplayCredentials(): DandanplayCredentials? {
    val appId = preferences.dandanplayAppId.get().trim()
    val appSecret = preferences.dandanplayAppSecret.get().trim()
    if (appId.isNotBlank() && appSecret.isNotBlank()) {
      return DandanplayCredentials(appId, appSecret, isUserConfigured = true)
    }
    return null
  }

  private fun configuredOAuthClientId(): String {
    val appId = preferences.dandanplayAppId.get().trim()
    if (appId.isBlank()) {
      throw IOException("Configure dandanplay AppId before authorization")
    }
    return appId
  }

  private fun explainDandanplayAuthError(
    message: String?,
    errorCode: Int,
    credentials: DandanplayCredentials,
  ): String? {
    if (message.isNullOrBlank()) {
      return if (errorCode != 0) "Dandanplay request failed (code: $errorCode)" else null
    }

    val isCredentialOrPermissionError =
      message.contains("应用不存在") ||
        message.contains("无权限") ||
        message.contains("Invalid AppId", ignoreCase = true) ||
        message.contains("Invalid AppSecret", ignoreCase = true) ||
        message.contains("invalid app", ignoreCase = true)

    if (!isCredentialOrPermissionError) return message

    val credentialSource = if (credentials.isUserConfigured) {
      "Configured dandanplay AppId/AppSecret"
    } else {
      "Built-in dandanplay public API credentials"
    }
    val suffix = if (errorCode != 0) " (code: $errorCode)" else ""
    return "$credentialSource cannot access login/send-comment APIs$suffix. " +
      "Use an authorized dandanplay Open Platform app or an authorized backend proxy. Original error: $message"
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

  /**
   * Test LLM connection with the current preferences.
   * Returns a human-readable result message.
   */
  suspend fun testLlmConnection(): String = withContext(Dispatchers.IO) {
    val baseUrl = preferences.llmApiBaseUrl.get().trimEnd('/')
    val apiKey = preferences.llmApiKey.get()
    val model = preferences.llmModel.get()

    if (baseUrl.isBlank()) return@withContext "Error: API Base URL is empty"
    if (apiKey.isBlank()) return@withContext "Error: API Key is empty"

    try {
      val requestBody = json.encodeToString(
        LlmChatRequest.serializer(),
        LlmChatRequest(
          model = model.ifBlank { "gpt-4o-mini" },
          messages = listOf(
            LlmMessage(role = "user", content = "Reply with only: OK"),
          ),
          temperature = 0.0,
        ),
      )

      val mediaType = "application/json".toMediaType()
      val request = Request.Builder()
        .url("$baseUrl/chat/completions")
        .post(requestBody.toRequestBody(mediaType))
        .header("Authorization", "Bearer $apiKey")
        .header("Content-Type", "application/json")
        .build()

      llmClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
          val body = response.body.string().take(200)
          return@withContext "Error: HTTP ${response.code}\n$body"
        }
        val responseBody = response.body.string()
        val chatResponse = json.decodeFromString<LlmChatResponse>(responseBody)
        val content = chatResponse.choices?.firstOrNull()?.message?.content ?: "(empty response)"
        val usedModel = chatResponse.model ?: model
        "Connected successfully\nModel: $usedModel\nResponse: $content"
      }
    } catch (e: Exception) {
      "Error: ${e.message}"
    }
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

    fun cleanCandidateTitle(name: String): String = cleanName(name)

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
      matchRegex(source, Regex("""^(.*?)\s+[sS](\d+)\s*[_\-\.\s]\s*(\d+[\.vV]?\d*)""")) {
        parsed(cleanName(groupValues[1]), groupValues[2].toIntOrNull(), parseEpisodeToken(groupValues[3]))
      }?.let { return it }

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

      matchRegex(source, Regex("""^(.*?)\s*\[[^\]\d]+]\s*\[(\d+[\.vV]?\d*)\]""")) {
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

      matchRegex(source, Regex("""^(.*?)\s+(\d{1,3})(?:\s+[^(\[].*)?$""")) {
        parsed(cleanName(groupValues[1]), null, parseEpisodeToken(groupValues[2]))
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
      dropLeadingReleaseTags(name)
        .replace(Regex("""[_\.\[\]]"""), " ")
        .replace(Regex("""第\s*\d+\s*[季部]"""), " ")
        .replace(Regex("""第[一二三四五六七八九十百千万]+[季部]"""), " ")
        .replace(Regex("""\b(?:TV\s*)?\d{1,3}\s*-\s*\d{1,3}(?:\+.*)?$""", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("""\b(?:BDRip|BluRay|WebRip|WEB-DL|HEVC|AAC|ASSx?\d*|FLACx?\d*|FLAC|Hi10p|Ma10p|10bit|8bit|x264|x265|H264|H265|UHD|HDR|MKV|MP4|TV全集|全集|特典映像|简繁外挂|日英双语)\b""", RegexOption.IGNORE_CASE), " ")
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

    private fun dropLeadingReleaseTags(value: String): String {
      var result = value.trim()
      while (true) {
        val match = Regex("""^(?:\[([^\]]+)]|\(([^)]+)\)|（([^）]+)）)\s*""").find(result) ?: break
        val rest = result.substring(match.range.last + 1).trimStart()
        if (!shouldDropLeadingBracketGroup(rest)) break
        result = rest
      }
      return result
    }

    private fun shouldDropLeadingBracketGroup(rest: String): Boolean {
      val normalized = rest.trimStart()
      if (normalized.isBlank()) return false

      val nextGroup = Regex("""^(?:\[([^\]]+)]|\(([^)]+)\)|（([^）]+)）)""").find(normalized)
      if (nextGroup != null) {
        val inner = nextGroup.groupValues.drop(1).firstOrNull { it.isNotBlank() }.orEmpty()
        return parseEpisodeToken(inner) == null
      }

      return !normalized.matches(
        Regex("""^[-_\s]*(?:\d{1,4}(?:[.\s_vV-].*)?|[sS]\d{1,2}\b.*|[eE][pP]?\d{1,4}\b.*|第\s*\d+[话集回].*)"""),
      )
    }

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

    private fun randomBase64Url(byteCount: Int): String {
      val bytes = ByteArray(byteCount)
      SecureRandom().nextBytes(bytes)
      return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun sha256Base64Url(input: String): String {
      val digest = MessageDigest.getInstance("SHA-256")
        .digest(input.toByteArray(StandardCharsets.UTF_8))
      return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    private fun tokenExpiresAt(expiresInSeconds: Long?): Long {
      if (expiresInSeconds == null || expiresInSeconds <= 0L) return 0L
      return System.currentTimeMillis() + expiresInSeconds * 1000L
    }

    private fun isTokenExpiringSoon(expiresAtMillis: Long): Boolean =
      expiresAtMillis != 0L && expiresAtMillis - System.currentTimeMillis() <= TOKEN_REFRESH_MARGIN_MS

    private const val CHUNK_SIZE = 16L * 1024L * 1024L
    private const val TAG = "DandanplayDanmakuRepo"
    const val API_SERVER = "https://api.dandanplay.net"
    const val USER_AGENT = "mpvDanmuku/1.0"
    const val DEFAULT_APP_ID = "gz2wnihj9d"
    private const val DEFAULT_APP_SECRET = "qo9N3YVARNZc7MWHce7v92q1SUe5uYJ8"
    private const val DEFAULT_OAUTH_REDIRECT_URI = "mpvdanmuku://dandanplay/oauth"
    private const val DEFAULT_OAUTH_SCOPE = "basic profile"
    private const val TOKEN_REFRESH_MARGIN_MS = 5L * 60L * 1000L
    const val DUMMY_HASH = "a1b2c3d4e5f67890abcd1234ef567890"
  }
}

@Serializable
private data class SearchResponseDto(
  val animes: List<AnimeDto> = emptyList(),
)

@Serializable
private data class AnimeDto(
  val bangumiId: JsonElement? = null,
  val animeTitle: String,
  val type: String? = null,
  val typeDescription: String? = null,
  val episodeCount: Int? = null,
) {
  val bangumiIdLong: Long?
    get() = bangumiId?.jsonPrimitive?.contentOrNull?.toLongOrNull()
}

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

@Serializable
private data class SendCommentRequestDto(
  val time: Double,
  val mode: Int,
  val color: Int,
  val comment: String,
)

@Serializable
private data class ProxySendCommentRequestDto(
  val episodeId: Long,
  val time: Double,
  val mode: Int,
  val color: Int,
  val comment: String,
)

@Serializable
private data class SendCommentResponseDto(
  val success: Boolean = false,
  val errorCode: Int = 0,
  val errorMessage: String? = null,
  val cid: Long = 0,
)

@Serializable
private data class OAuthTokenRequestDto(
  @SerialName("client_id") val clientId: String,
  @SerialName("client_secret") val clientSecret: String? = null,
  val code: String? = null,
  @SerialName("redirect_uri") val redirectUri: String? = null,
  @SerialName("grant_type") val grantType: String,
  @SerialName("code_verifier") val codeVerifier: String? = null,
  @SerialName("refresh_token") val refreshToken: String? = null,
)

@Serializable
private data class OAuthTokenResponseDto(
  @SerialName("access_token") val accessToken: String,
  @SerialName("token_type") val tokenType: String? = null,
  @SerialName("expires_in") val expiresIn: Long? = null,
  @SerialName("refresh_token") val refreshToken: String? = null,
  val scope: String? = null,
)

@Serializable
private data class LlmChatRequest(
  val model: String,
  val messages: List<LlmMessage>,
  val temperature: Double = 0.1,
)

@Serializable
private data class LlmMessage(
  val role: String,
  val content: String,
)

@Serializable
private data class LlmChatResponse(
  val choices: List<LlmChoice>? = null,
  val model: String? = null,
)

@Serializable
private data class LlmChoice(
  val message: LlmMessage? = null,
)

@Serializable
private data class LlmTitleParseResult(
  val title: String = "",
  val season: Int? = null,
  val episode: Int? = null,
  val alternativeTitles: List<String> = emptyList(),
)
