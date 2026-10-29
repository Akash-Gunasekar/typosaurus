package com.example.aikeyboard.core.provider

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * One automatic retry (429: after Retry-After, max 5 s; 5xx: after 1 s), then the optional
 * fallback provider. Nothing is retried once text has started streaming, so output is never duplicated.
 */
class ResilientStreamer(
    private val providers: ProviderLookup,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    fun stream(
        primaryId: String,
        fallbackId: String?,
        requestFor: (providerId: String) -> ChatRequest,
    ): Flow<LlmEvent> = flow {
        var emittedText = false

        suspend fun attempt(id: String): LlmException? = try {
            providers.get(id).stream(requestFor(id)).collect { e ->
                if (e is LlmEvent.Delta) emittedText = true
                emit(e)
            }
            null
        } catch (e: LlmException) {
            if (emittedText) throw e
            e
        }

        var error = attempt(primaryId)
        if (error != null) {
            val wait = when (error.kind) {
                LlmException.Kind.RateLimit -> ((error.retryAfterSeconds ?: 1).coerceIn(0, 5)) * 1000
                LlmException.Kind.Server -> 1000L
                else -> -1L
            }
            if (wait >= 0) {
                sleep(wait)
                error = attempt(primaryId)
            }
        }
        if (error != null && fallbackId != null && fallbackId != primaryId && error.isTransient) {
            error = attempt(fallbackId)
        }
        if (error != null) throw error
    }
}
