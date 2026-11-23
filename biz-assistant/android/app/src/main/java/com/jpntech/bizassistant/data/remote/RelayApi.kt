package com.jpntech.bizassistant.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class RelayException(val status: Int, message: String) : Exception(message)

/** Vercel 중계 서버 클라이언트. API 키는 서버에만 있고, 앱은 공유 토큰만 보낸다. */
class RelayApi(
    private val baseUrlProvider: suspend () -> String,
    private val tokenProvider: suspend () -> String,
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun health(): HealthResponse = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url("/api/health")).get().build()
        client.newCall(req).execute().use { res ->
            val body = res.body?.string().orEmpty()
            runCatching { json.decodeFromString(HealthResponse.serializer(), body) }
                .getOrElse { throw RelayException(res.code, "서버 응답을 읽을 수 없습니다 (${res.code})") }
        }
    }

    suspend fun analyze(request: AnalyzeRequest): AnalyzeResponse =
        post("/api/analyze", json.encodeToString(AnalyzeRequest.serializer(), request)) {
            json.decodeFromString(AnalyzeResponse.serializer(), it)
        }

    suspend fun draftReply(analysis: Analysis, tone: String = "formal"): String =
        post("/api/draft-reply", json.encodeToString(DraftReplyRequest.serializer(), DraftReplyRequest(analysis, tone))) {
            json.decodeFromString(DraftReplyResponse.serializer(), it).reply
        }

    /** 녹음 조각(4MB 이하)을 올려 글자로 받는다. */
    suspend fun transcribe(bytes: ByteArray, contentType: String, callerHint: String?): String = withContext(Dispatchers.IO) {
        val token = tokenProvider()
        if (token.isBlank()) throw RelayException(0, "설정에서 서버 토큰을 먼저 입력하세요")
        val builder = Request.Builder()
            .url(url("/api/transcribe"))
            .header("x-app-token", token)
            .post(bytes.toRequestBody(contentType.toMediaType()))
        if (!callerHint.isNullOrBlank()) builder.header("x-caller-hint", java.net.URLEncoder.encode(callerHint, "UTF-8"))
        try {
            longClient.newCall(builder.build()).execute().use { res ->
                val text = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    val msg = runCatching { json.decodeFromString(ErrorResponse.serializer(), text).error }.getOrDefault("서버 오류 (${res.code})")
                    throw RelayException(res.code, msg)
                }
                runCatching { json.decodeFromString(TranscribeResponse.serializer(), text).text }
                    .getOrElse { throw RelayException(res.code, "응답 형식이 올바르지 않습니다") }
            }
        } catch (e: IOException) {
            throw RelayException(0, "서버에 연결하지 못했습니다: ${e.message ?: "네트워크 오류"}")
        }
    }

    private val longClient: OkHttpClient by lazy {
        client.newBuilder().readTimeout(180, TimeUnit.SECONDS).writeTimeout(180, TimeUnit.SECONDS).build()
    }

    private suspend fun <T> post(path: String, body: String, parse: (String) -> T): T = withContext(Dispatchers.IO) {
        val token = tokenProvider()
        if (token.isBlank()) throw RelayException(0, "설정에서 서버 토큰을 먼저 입력하세요")
        val req = Request.Builder()
            .url(url(path))
            .header("x-app-token", token)
            .post(body.toRequestBody(jsonType))
            .build()
        try {
            client.newCall(req).execute().use { res ->
                val text = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    val msg = runCatching { json.decodeFromString(ErrorResponse.serializer(), text).error }
                        .getOrDefault("서버 오류 (${res.code})")
                    throw RelayException(res.code, msg)
                }
                runCatching { parse(text) }.getOrElse { throw RelayException(res.code, "응답 형식이 올바르지 않습니다") }
            }
        } catch (e: IOException) {
            throw RelayException(0, "서버에 연결하지 못했습니다: ${e.message ?: "네트워크 오류"}")
        }
    }

    private suspend fun url(path: String): String = baseUrlProvider().trimEnd('/') + path
}
