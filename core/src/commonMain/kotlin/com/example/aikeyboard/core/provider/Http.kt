package com.example.aikeyboard.core.provider

import kotlinx.coroutines.flow.Flow

/**
 * The little HTTP the adapters need. OkHttp backs it on Android/JVM and NSURLSession (via Ktor's
 * Darwin engine) on iOS. Implementations throw [LlmException] for network errors and non-2xx replies
 * (use [httpError]), and stop the request when the calling coroutine is cancelled.
 */
interface HttpEngine {
    /** GET when [body] is null, otherwise a JSON POST. Returns the response body. */
    suspend fun send(url: String, headers: Map<String, String>, body: String? = null): String

    /** JSON POST that emits the payload of each server-sent-event `data:` line. */
    fun sse(url: String, headers: Map<String, String>, body: String): Flow<String>
}

/** Platform default (OkHttp / Darwin) with the keyboard's timeouts. */
expect fun defaultHttpEngine(): HttpEngine

/** Error text never includes request content; only a short slice of the provider's error body. */
fun httpError(code: Int, body: String, retryAfter: String? = null): LlmException {
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
