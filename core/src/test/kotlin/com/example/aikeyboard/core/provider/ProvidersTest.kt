package com.example.aikeyboard.core.provider

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ProvidersTest {
    private lateinit var server: MockWebServer
    private val client = OkHttpClient()

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    private fun base() = server.url("/v1").toString().trimEnd('/')
    private fun sse(body: String) = MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body)
    private fun openAi() = OpenAiCompatibleProvider("t", base(), { "key" }, client)
    private fun text(events: List<LlmEvent>) = events.filterIsInstance<LlmEvent.Delta>().joinToString("") { it.text }

    @Test fun openAiStreamEmitsDeltas() = runTest {
        server.enqueue(sse(
            "data: {\"choices\":[{\"delta\":{\"content\":\"Hel\"}}]}\n\n" +
                "data: {\"choices\":[{\"delta\":{\"content\":\"lo\"},\"finish_reason\":\"stop\"}]}\n\n" +
                "data: [DONE]\n\n"))
        val events = openAi().stream(ChatRequest("m", "s", "u")).toList()
        assertEquals("Hello", text(events))
        assertEquals("stop", (events.first { it is LlmEvent.Done } as LlmEvent.Done).stopReason)
        val req = server.takeRequest()
        assertEquals("/v1/chat/completions", req.path)
        assertEquals("Bearer key", req.getHeader("Authorization"))
        assertTrue(req.body.readUtf8().contains("\"max_tokens\":1024"))
    }

    @Test fun openAiIgnoresCommentLinesAndRoleOnlyDeltas() = runTest {
        server.enqueue(sse(
            ": OPENROUTER PROCESSING\n\n" +
                "data: {\"choices\":[{\"delta\":{\"role\":\"assistant\",\"content\":\"\"}}]}\n\n" +
                "data: {\"choices\":[{\"delta\":{\"content\":\"ok\"}}]}\n\n" +
                "data: [DONE]\n\n"))
        assertEquals("ok", text(openAi().stream(ChatRequest("m", "s", "u")).toList()))
    }

    @Test fun openAiQuirksSwapParameters() = runTest {
        server.enqueue(sse("data: [DONE]\n\n"))
        openAi().stream(ChatRequest("o3", "s", "u", quirks = ModelQuirks(true, true))).toList()
        val body = server.takeRequest().body.readUtf8()
        assertTrue("max_completion_tokens" in body && "\"max_tokens\"" !in body && "temperature" !in body, body)
    }

    @Test fun openAiNonStreaming() = runTest {
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"Whole"},"finish_reason":"stop"}]}"""))
        val events = openAi().stream(ChatRequest("m", "s", "u", stream = false)).toList()
        assertEquals("Whole", text(events))
    }

    @Test fun openAiListModelsSorted() = runTest {
        server.enqueue(MockResponse().setBody("""{"data":[{"id":"b"},{"id":"a"}]}"""))
        assertEquals(listOf("a", "b"), openAi().listModels())
    }

    @Test fun ollamaFallsBackToTags() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("not found"))
        server.enqueue(MockResponse().setBody("""{"models":[{"name":"llama3"},{"name":"gemma"}]}"""))
        val p = OpenAiCompatibleProvider("ollama_cloud", base(), { "k" }, client, ollamaTagsFallback = true)
        assertEquals(listOf("gemma", "llama3"), p.listModels())
        server.takeRequest()
        assertEquals("/api/tags", server.takeRequest().path)
    }

    @Test fun anthropicStreamEmitsDeltas() = runTest {
        server.enqueue(sse(
            "event: message_start\ndata: {\"type\":\"message_start\"}\n\n" +
                "event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"Hi \"}}\n\n" +
                "event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"there\"}}\n\n" +
                "event: message_delta\ndata: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"max_tokens\"}}\n\n" +
                "event: message_stop\ndata: {\"type\":\"message_stop\"}\n\n"))
        val p = AnthropicProvider({ "ak" }, client, base())
        val events = p.stream(ChatRequest("m", "sys", "u")).toList()
        assertEquals("Hi there", text(events))
        assertEquals("max_tokens", (events.last() as LlmEvent.Done).stopReason)
        val req = server.takeRequest()
        assertEquals("ak", req.getHeader("x-api-key"))
        assertEquals("2023-06-01", req.getHeader("anthropic-version"))
        val body = req.body.readUtf8()
        assertTrue("\"system\":\"sys\"" in body)
        assertTrue("temperature" !in body, body) // Claude Sonnet 5 and later reject sampling parameters
    }

    @Test fun anthropicErrorEventMapsKind() = runTest {
        server.enqueue(sse("data: {\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"busy\"}}\n\n"))
        val e = assertFailsWith<LlmException> { AnthropicProvider({ "k" }, client, base()).stream(ChatRequest("m", "s", "u")).toList() }
        assertEquals(LlmException.Kind.Server, e.kind)
    }

    @Test fun anthropicNonStreaming() = runTest {
        server.enqueue(MockResponse().setBody("""{"content":[{"type":"text","text":"A"},{"type":"text","text":"B"}],"stop_reason":"end_turn"}"""))
        val ev = AnthropicProvider({ "k" }, client, base()).stream(ChatRequest("m", "s", "u", stream = false)).toList()
        assertEquals("AB", text(ev))
    }

    @Test fun httpErrorsMapToKinds() = runTest {
        suspend fun kindFor(code: Int, headers: Map<String, String> = emptyMap()): LlmException {
            server.enqueue(MockResponse().setResponseCode(code).apply { headers.forEach { (k, v) -> setHeader(k, v) } }
                .setBody("""{"error":{"message":"nope"}}"""))
            return assertFailsWith { openAi().stream(ChatRequest("m", "s", "u")).toList() }
        }
        assertEquals(LlmException.Kind.Auth, kindFor(401).kind)
        assertEquals(LlmException.Kind.Auth, kindFor(403).kind)
        assertEquals(LlmException.Kind.BadRequest, kindFor(400).kind)
        assertEquals(LlmException.Kind.Server, kindFor(503).kind)
        val rl = kindFor(429, mapOf("Retry-After" to "3"))
        assertEquals(LlmException.Kind.RateLimit, rl.kind); assertEquals(3L, rl.retryAfterSeconds)
        assertEquals("nope", rl.message)
    }

    @Test fun readTimeoutMapsToTimeout() = runTest {
        server.enqueue(MockResponse().setBodyDelay(2, java.util.concurrent.TimeUnit.SECONDS).setBody("data: [DONE]\n\n"))
        val fast = OkHttpClient.Builder().readTimeout(200, java.util.concurrent.TimeUnit.MILLISECONDS).build()
        val p = OpenAiCompatibleProvider("t", base(), { null }, fast)
        val e = assertFailsWith<LlmException> { p.stream(ChatRequest("m", "s", "u")).toList() }
        assertEquals(LlmException.Kind.Timeout, e.kind)
    }

    @Test fun baseUrlPolicy() {
        assertEquals(null, BaseUrlPolicy.validate("https://example.com/v1"))
        assertEquals(null, BaseUrlPolicy.validate("http://localhost:11434/v1"))
        assertEquals(null, BaseUrlPolicy.validate("http://10.0.2.2:11434/v1"))
        assertTrue(BaseUrlPolicy.validate("http://example.com/v1") != null)
        assertTrue(BaseUrlPolicy.validate("ftp://x") != null)
        assertTrue(BaseUrlPolicy.validate("nonsense") != null)
    }

    @Test fun quirkDefaults() {
        assertTrue(ModelQuirks.defaultsFor("openai", "o3-mini").omitTemperature)
        assertTrue(!ModelQuirks.defaultsFor("openai", "gpt-4o").omitTemperature)
        assertTrue(!ModelQuirks.defaultsFor("groq", "o3-like").omitTemperature)
    }
}

class ProviderRegistryTest {
    private fun registry(key: String? = "k", s: com.example.aikeyboard.core.action.AppSettings = com.example.aikeyboard.core.action.AppSettings()) =
        ProviderRegistry({ key }, { s })

    @Test fun unknownProviderIsConfigError() {
        assertEquals(LlmException.Kind.Config, assertFailsWith<LlmException> { registry().get("nope") }.kind)
    }

    @Test fun customNeedsBaseUrlAndHttps() {
        assertEquals(LlmException.Kind.Config, assertFailsWith<LlmException> { registry().get("custom") }.kind)
        val bad = com.example.aikeyboard.core.action.AppSettings(baseUrls = mapOf("custom" to "http://evil.example.com"))
        assertEquals(LlmException.Kind.Config, assertFailsWith<LlmException> { registry(s = bad).get("custom") }.kind)
        val ok = com.example.aikeyboard.core.action.AppSettings(baseUrls = mapOf("custom" to "http://localhost:11434/v1"))
        assertEquals("custom", registry(s = ok).get("custom").id)
    }

    @Test fun missingKeyIsConfigErrorBeforeAnyRequest() = kotlinx.coroutines.test.runTest {
        val e = assertFailsWith<LlmException> { registry(key = null).get("openai").stream(ChatRequest("m", "s", "u")).toList() }
        assertEquals(LlmException.Kind.Config, e.kind)
        assertTrue("OpenAI" in e.message!!)
    }

    @Test fun catalogCoversAllFiveProvidersPlusCustom() {
        assertEquals(setOf("openai", "anthropic", "groq", "openrouter", "ollama_cloud", "custom"), ProviderCatalog.all.map { it.id }.toSet())
    }
}
