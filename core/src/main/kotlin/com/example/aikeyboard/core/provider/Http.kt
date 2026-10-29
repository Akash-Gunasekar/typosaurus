package com.example.aikeyboard.core.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException

internal fun IOException.toLlm(): LlmException = when (this) {
    is InterruptedIOException -> LlmException(LlmException.Kind.Timeout, "Timed out", cause = this)
    else -> LlmException(LlmException.Kind.Network, "Network error", cause = this)
}

/** Error text never includes request content; only a short slice of the provider's error body. */
internal fun httpError(code: Int, body: String, retryAfter: String? = null): LlmException {
    val kind = when (code) {
        401, 403 -> LlmException.Kind.Auth
        408 -> LlmException.Kind.Timeout
        429 -> LlmException.Kind.RateLimit
        in 400..499 -> LlmException.Kind.BadRequest
        else -> LlmException.Kind.Server
    }
    val message = providerMessage(body) ?: body.take(200)
    return LlmException(kind, message, code, retryAfter?.trim()?.toLongOrNull())
}

private val messageRegex = Regex(""""message"\s*:\s*"((?:[^"\\]|\\.)*)"""")
private fun providerMessage(body: String): String? =
    messageRegex.find(body)?.groupValues?.get(1)?.replace("\\n", " ")?.replace("\\\"", "\"")?.take(200)

/** Blocking execute on IO that cancels the OkHttp call when the coroutine is cancelled. */
internal suspend fun OkHttpClient.executeSuspend(request: Request): Response = withContext(Dispatchers.IO) {
    val call = newCall(request)
    val handle = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
    try {
        call.execute()
    } catch (e: IOException) {
        currentCoroutineContext().ensureActive()
        throw e.toLlm()
    } finally {
        handle?.dispose()
    }
}

internal suspend fun OkHttpClient.getJson(request: Request): String =
    executeSuspend(request).use { res ->
        val body = withContext(Dispatchers.IO) { res.body?.string().orEmpty() }
        if (!res.isSuccessful) throw httpError(res.code, body, res.header("Retry-After"))
        body
    }

/** Emits the payload of each `data:` line of a server-sent-event response. */
internal fun OkHttpClient.sseData(request: Request): Flow<String> = flow {
    val call = newCall(request)
    val handle = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
    try {
        val response = try {
            call.execute()
        } catch (e: IOException) {
            currentCoroutineContext().ensureActive()
            throw e.toLlm()
        }
        response.use { res ->
            if (!res.isSuccessful) throw httpError(res.code, res.body?.string().orEmpty(), res.header("Retry-After"))
            val source = res.body?.source() ?: throw LlmException(LlmException.Kind.Server, "Empty response")
            while (true) {
                currentCoroutineContext().ensureActive()
                val line = try {
                    source.readUtf8Line()
                } catch (e: IOException) {
                    if (!currentCoroutineContext().isActive) throw e
                    throw e.toLlm()
                } ?: break
                if (line.startsWith("data:")) emit(line.removePrefix("data:").trim())
            }
        }
    } finally {
        handle?.dispose()
    }
}.flowOn(Dispatchers.IO)
