package com.example.aikeyboard.core.provider

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformWhile
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class AnthropicProvider(
    private val apiKey: suspend () -> String?,
    private val client: OkHttpClient,
    baseUrl: String = "https://api.anthropic.com/v1",
    private val json: Json = Json { ignoreUnknownKeys = true },
) : LlmProvider {
    override val id = "anthropic"
    private val base = baseUrl.trimEnd('/')

    private suspend fun Request.Builder.auth(): Request.Builder = apply {
        apiKey()?.takeIf { it.isNotBlank() }?.let { header("x-api-key", it) }
        header("anthropic-version", "2023-06-01")
    }

    internal fun buildBody(r: ChatRequest): String = buildJsonObject {
        put("model", r.model)
        put("max_tokens", r.maxTokens)
        // No temperature/top_p/top_k: current Claude models reject sampling parameters with a 400.
        put("system", r.system)
        put("stream", r.stream)
        putJsonArray("messages") { addJsonObject { put("role", "user"); put("content", r.user) } }
    }.toString()

    override fun stream(request: ChatRequest): Flow<LlmEvent> = flow {
        val http = Request.Builder().url("$base/messages").auth()
            .post(buildBody(request).toRequestBody(JSON)).build()

        if (!request.stream) {
            val root = parse(client.getJson(http))
            val text = root["content"]?.jsonArray
                ?.filter { it.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "text" }
                ?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }.orEmpty()
            if (text.isNotEmpty()) emit(LlmEvent.Delta(text))
            emit(LlmEvent.Done(root["stop_reason"]?.jsonPrimitive?.contentOrNull))
            return@flow
        }

        var stopReason: String? = null
        client.sseData(http).transformWhile { data ->
            val obj = parse(data)
            when (obj["type"]?.jsonPrimitive?.contentOrNull) {
                "content_block_delta" -> obj["delta"]?.jsonObject
                    ?.takeIf { it["type"]?.jsonPrimitive?.contentOrNull == "text_delta" }
                    ?.get("text")?.jsonPrimitive?.contentOrNull
                    ?.takeIf { it.isNotEmpty() }?.let { emit(LlmEvent.Delta(it)) }
                "message_delta" -> obj["delta"]?.jsonObject?.get("stop_reason")
                    ?.jsonPrimitive?.contentOrNull?.let { stopReason = it }
                "message_stop" -> {
                    emit(LlmEvent.Done(stopReason))
                    return@transformWhile false
                }
                "error" -> throw streamError(obj["error"] as? JsonObject)
            }
            true
        }.collect { emit(it) }
    }

    override suspend fun listModels(): List<String> {
        val body = client.getJson(Request.Builder().url("$base/models?limit=100").auth().get().build())
        return parse(body)["data"]!!.jsonArray
            .mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull }
    }

    private fun streamError(err: JsonObject?): LlmException {
        val msg = err?.get("message")?.jsonPrimitive?.contentOrNull ?: "Provider error"
        val kind = when (err?.get("type")?.jsonPrimitive?.contentOrNull) {
            "authentication_error", "permission_error" -> LlmException.Kind.Auth
            "rate_limit_error" -> LlmException.Kind.RateLimit
            "invalid_request_error" -> LlmException.Kind.BadRequest
            else -> LlmException.Kind.Server // api_error, overloaded_error, ...
        }
        return LlmException(kind, msg)
    }

    private fun parse(text: String): JsonObject = try {
        json.parseToJsonElement(text).jsonObject
    } catch (e: SerializationException) {
        throw LlmException(LlmException.Kind.Server, "Unreadable response from provider", cause = e)
    } catch (e: IllegalArgumentException) {
        throw LlmException(LlmException.Kind.Server, "Unreadable response from provider", cause = e)
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
