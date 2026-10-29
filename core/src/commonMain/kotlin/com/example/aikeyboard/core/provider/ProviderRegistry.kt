package com.example.aikeyboard.core.provider

import com.example.aikeyboard.core.action.AppSettings

enum class ProviderKind { OpenAiCompatible, Anthropic }

data class ProviderInfo(
    val id: String,
    val displayName: String,
    val kind: ProviderKind,
    val defaultBaseUrl: String,
    val baseUrlEditable: Boolean = false,
    val keyRequired: Boolean = true,
    val privacyUrl: String? = null,
    val extraHeaders: Map<String, String> = emptyMap(),
    val ollamaTagsFallback: Boolean = false,
)

/** Verify URLs and headers against each provider's current docs before release. */
object ProviderCatalog {
    val all: List<ProviderInfo> = listOf(
        ProviderInfo("openai", "OpenAI", ProviderKind.OpenAiCompatible, "https://api.openai.com/v1",
            privacyUrl = "https://openai.com/policies/privacy-policy"),
        ProviderInfo("anthropic", "Anthropic", ProviderKind.Anthropic, "https://api.anthropic.com/v1",
            privacyUrl = "https://www.anthropic.com/legal/privacy"),
        ProviderInfo("groq", "Groq", ProviderKind.OpenAiCompatible, "https://api.groq.com/openai/v1",
            privacyUrl = "https://groq.com/privacy-policy"),
        ProviderInfo("openrouter", "OpenRouter", ProviderKind.OpenAiCompatible, "https://openrouter.ai/api/v1",
            privacyUrl = "https://openrouter.ai/privacy",
            extraHeaders = mapOf("X-Title" to "Typosaurus")),
        ProviderInfo("ollama_cloud", "Ollama Cloud", ProviderKind.OpenAiCompatible, "https://ollama.com/v1",
            baseUrlEditable = true, ollamaTagsFallback = true, privacyUrl = "https://ollama.com/privacy"),
        ProviderInfo("custom", "Custom (OpenAI-compatible)", ProviderKind.OpenAiCompatible, "",
            baseUrlEditable = true, keyRequired = false),
    )

    fun find(id: String): ProviderInfo? = all.firstOrNull { it.id == id }
    fun require(id: String): ProviderInfo = find(id) ?: throw IllegalArgumentException("Unknown provider $id")
}

object BaseUrlPolicy {
    private val localHosts = setOf("localhost", "127.0.0.1", "10.0.2.2")

    /** scheme://host[:port][/path...]: enough to check scheme and host without java.net.URI. */
    private val urlRegex = Regex("""^([A-Za-z][A-Za-z0-9+.-]*)://([^/:?#\s]+)(:\d{1,5})?([/?#]\S*)?$""")

    /** https always; plain http only for loopback / emulator-host (matches network_security_config). */
    fun validate(url: String): String? {
        val m = urlRegex.matchEntire(url.trim()) ?: return "Not a valid URL"
        val host = m.groupValues[2].lowercase()
        return when (m.groupValues[1].lowercase()) {
            "https" -> null
            "http" -> if (host in localHosts) null else "Use https:// (plain http is only allowed for localhost)"
            else -> "URL must start with https://"
        }
    }
}

fun interface ApiKeys {
    suspend fun get(providerId: String): String?
}

class ProviderRegistry(
    private val keys: ApiKeys,
    private val settings: () -> AppSettings,
    private val http: HttpEngine = defaultHttpEngine(),
) : ProviderLookup {

    override fun get(id: String): LlmProvider {
        val info = ProviderCatalog.find(id)
            ?: throw LlmException(LlmException.Kind.Config, "Unknown provider \"$id\"")
        val baseUrl = settings().baseUrls[id]?.takeIf { it.isNotBlank() } ?: info.defaultBaseUrl
        if (baseUrl.isBlank()) throw LlmException(LlmException.Kind.Config, "Set a base URL for ${info.displayName} in Settings")
        BaseUrlPolicy.validate(baseUrl)?.let { throw LlmException(LlmException.Kind.Config, "${info.displayName}: $it") }
        val key: suspend () -> String? = {
            keys.get(id)?.takeIf { it.isNotBlank() }
                ?: if (info.keyRequired) throw LlmException(LlmException.Kind.Config, "Add your ${info.displayName} API key in Settings") else null
        }
        return when (info.kind) {
            ProviderKind.Anthropic -> AnthropicProvider(key, http, baseUrl)
            ProviderKind.OpenAiCompatible ->
                OpenAiCompatibleProvider(id, baseUrl, key, http, info.extraHeaders, info.ollamaTagsFallback)
        }
    }
}
