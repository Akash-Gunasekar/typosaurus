package com.example.aikeyboard.core.provider

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformWhile
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** OpenAI, Groq, OpenRouter, Ollama Cloud and any custom OpenAI-compatible endpoint. */
class OpenAiCompatibleProvider(
    override val id: String,
    baseUrl: String,
    private val apiKey: suspend () -> String?,
    private val http: HttpEngine,
    private val extraHeaders: Map<String, String> = emptyMap(),
    /** Ollama: fall back to the native /api/tags listing when /v1/models fails. */
    private val ollamaTagsFallback: Boolean = false,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : LlmProvider {

    private val base = baseUrl.trimEnd('/')

    private suspend fun headers(): Map<String, String> = buildMap {
        apiKey()?.takeIf { it.isNotBlank() }?.let { put("Authorization", "Bearer $it") }
        putAll(extraHeaders)
    }

    internal fun buildBody(r: ChatRequest): String = buildJsonObject {
        put("model", r.model)
        put("stream", r.stream)
        if (!r.quirks.omitTemperature) put("temperature", r.temperature)
        put(if (r.quirks.useMaxCompletionTokens) "max_completion_tokens" else "max_tokens", r.maxTokens)
        putJsonArray("messages") {
            addJsonObject { put("role", "system"); put("content", r.system) }
            addJsonObject { put("role", "user"); put("content", r.user) }
        }
    }.toString()

    override fun stream(request: ChatRequest): Flow<LlmEvent> {
        if (!request.stream) return flow {
            val root = parse(http.send("$base/chat/completions", headers(), buildBody(request)))
            val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            val text = choice?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull.orEmpty()
            if (text.isNotEmpty()) emit(LlmEvent.Delta(text))
            emit(LlmEvent.Done(choice?.get("finish_reason")?.jsonPrimitive?.contentOrNull))
        }
        return flow {
            http.sse("$base/chat/completions", headers(), buildBody(request)).transformWhile { data ->
                if (data == "[DONE]") {
                    emit(LlmEvent.Done(null))
                    return@transformWhile false
                }
                val obj = parse(data)
                obj["error"]?.let { throw LlmException(LlmException.Kind.Server, errorText(it)) }
                val choice = obj["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: return@transformWhile true
                choice["delta"]?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
                    ?.takeIf { it.isNotEmpty() }?.let { emit(LlmEvent.Delta(it)) }
                choice["finish_reason"]?.jsonPrimitive?.contentOrNull?.let { emit(LlmEvent.Done(it)) }
                true
            }.collect { emit(it) }
        }
    }

    override suspend fun listModels(): List<String> {
        return try {
            val body = http.send("$base/models", headers())
            parse(body)["data"]!!.jsonArray
                .mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull }.sorted()
        } catch (e: LlmException) {
            if (!ollamaTagsFallback || e.kind == LlmException.Kind.Auth) throw e
            val root = base.removeSuffix("/v1")
            val body = http.send("$root/api/tags", headers())
            parse(body)["models"]!!.jsonArray
                .mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }.sorted()
        }
    }

    private fun parse(text: String): JsonObject = try {
        json.parseToJsonElement(text).jsonObject
    } catch (e: SerializationException) {
        throw LlmException(LlmException.Kind.Server, "Unreadable response from provider", cause = e)
    } catch (e: IllegalArgumentException) {
        throw LlmException(LlmException.Kind.Server, "Unreadable response from provider", cause = e)
    }

    private fun errorText(e: JsonElement): String =
        (e as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull ?: (e as? JsonPrimitive)?.contentOrNull ?: "Provider error"
}
