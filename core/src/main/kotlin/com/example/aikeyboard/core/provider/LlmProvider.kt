package com.example.aikeyboard.core.provider

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/** Per-model parameter differences (some newer OpenAI models reject max_tokens / temperature). */
@Serializable
data class ModelQuirks(
    val useMaxCompletionTokens: Boolean = false,
    val omitTemperature: Boolean = false,
) {
    companion object {
        private val reasoningPrefixes = listOf("o1", "o3", "o4", "gpt-5", "gpt-6")

        /** Heuristic only; users can override per model in Settings > Providers > Advanced. */
        fun defaultsFor(providerId: String, model: String): ModelQuirks {
            if (providerId != "openai") return ModelQuirks()
            val m = model.lowercase()
            return if (reasoningPrefixes.any { m.startsWith(it) }) ModelQuirks(true, true) else ModelQuirks()
        }
    }
}

data class ChatRequest(
    val model: String,
    val system: String,
    val user: String,
    val temperature: Double = 0.3,
    val maxTokens: Int = 1024,
    val stream: Boolean = true,
    val quirks: ModelQuirks = ModelQuirks(),
)

sealed interface LlmEvent {
    data class Delta(val text: String) : LlmEvent
    data class Done(val stopReason: String?) : LlmEvent
}

class LlmException(
    val kind: Kind,
    message: String,
    val httpCode: Int? = null,
    val retryAfterSeconds: Long? = null,
    cause: Throwable? = null,
) : Exception(message, cause) {
    /** [Config]: something the user must fix in Settings (missing key / model / base URL). */
    enum class Kind { Auth, Config, RateLimit, Server, Network, Timeout, BadRequest, Unknown }

    /** Worth trying the next provider. */
    val isTransient: Boolean get() = kind == Kind.RateLimit || kind == Kind.Server || kind == Kind.Network || kind == Kind.Timeout
}

fun LlmException.userMessage(): String = when (kind) {
    LlmException.Kind.Auth -> "API key rejected — check Settings › Providers"
    LlmException.Kind.Config -> message?.takeIf { it.isNotBlank() } ?: "Finish setup in Settings"
    LlmException.Kind.RateLimit -> "Rate limit reached — try again shortly"
    LlmException.Kind.Timeout -> "The request timed out — try again"
    LlmException.Kind.Network -> "Network problem — check your connection"
    LlmException.Kind.BadRequest -> message?.takeIf { it.isNotBlank() }?.let { "Request rejected: ${it.take(120)}" }
        ?: "Model rejected the request — check model settings"
    LlmException.Kind.Server -> "Provider error — try again"
    LlmException.Kind.Unknown -> "Something went wrong"
}

/** The user has to change something in Settings before retrying makes sense. */
val LlmException.needsSettings: Boolean get() = kind == LlmException.Kind.Auth || kind == LlmException.Kind.Config

interface LlmProvider {
    val id: String
    suspend fun listModels(): List<String>
    fun stream(request: ChatRequest): Flow<LlmEvent>
}

fun interface ProviderLookup {
    fun get(id: String): LlmProvider
}
