package com.example.aikeyboard.core.runner

import com.example.aikeyboard.core.action.AppSettings
import com.example.aikeyboard.core.provider.HttpEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The iOS-facing facade, exercised on the JVM with a scripted HTTP engine. */
@OptIn(ExperimentalCoroutinesApi::class)
class AssistantTest {
    private class FakeHttp(private val lines: List<String>) : HttpEngine {
        val calls = mutableListOf<Pair<String, Map<String, String>>>()
        override suspend fun send(url: String, headers: Map<String, String>, body: String?) = error("not used")
        override fun sse(url: String, headers: Map<String, String>, body: String): Flow<String> {
            calls += url to headers
            return flowOf(*lines.toTypedArray())
        }
    }

    private class Recorder : AssistantListener {
        val events = mutableListOf<String>()
        override fun onPartial(text: String) { events += "partial:$text" }
        override fun onResult(text: String, truncated: Boolean) { events += "result:$text" }
        override fun onError(message: String, openSettings: Boolean) { events += "error:$message" }
    }

    private val settings = Assistant.encodeSettings(
        AppSettings(consentGiven = true, defaultProviderId = "anthropic", defaultModels = mapOf("anthropic" to "claude-sonnet-5")),
    )

    /** Runs on the test dispatcher (so advanceUntilIdle drives it) but outside the test's job. */
    private fun kotlinx.coroutines.test.TestScope.newScope() = CoroutineScope(coroutineContext + Job())

    @Test fun runsAnActionThroughTheSharedRunner() = runTest {
        val scope = newScope()
        val http = FakeHttp(listOf(
            """{"type":"content_block_delta","delta":{"type":"text_delta","text":"The cat"}}""",
            """{"type":"content_block_delta","delta":{"type":"text_delta","text":" sat"}}""",
            """{"type":"message_stop"}""",
        ))
        val a = Assistant(settings, null, { "sk-test" }, scope, http)
        val r = Recorder()
        a.run(a.actionForKeyword("fix")!!, "teh cat sat", null, r); advanceUntilIdle()
        assertEquals("result:The cat sat", r.events.last())
        assertEquals("https://api.anthropic.com/v1/messages", http.calls.single().first)
        assertEquals("sk-test", http.calls.single().second["x-api-key"])
        scope.cancel()
    }

    @Test fun missingKeyAsksForSettings() = runTest {
        val scope = newScope()
        val a = Assistant(settings, null, { null }, scope, FakeHttp(emptyList()))
        val r = Recorder()
        a.run(a.actions.first(), "hello", null, r); advanceUntilIdle()
        assertTrue(r.events.last().startsWith("error:Add your Anthropic API key"), r.events.toString())
        scope.cancel()
    }

    @Test fun triggersAndSharedSettingsFormat() = runTest {
        val scope = newScope()
        val a = Assistant(settings, null, { "k" }, scope, FakeHttp(emptyList()))
        val m = a.matchTrigger("thanks for the update /tone:formal ")!!
        assertEquals("tone", m.keyword); assertEquals("formal", m.arg)
        assertEquals(8, a.toolbarActions.size)
        a.update(Assistant.encodeSettings(a.settings.copy(triggersEnabled = false)), null)
        assertEquals(null, a.matchTrigger("hi /fix "))
        scope.cancel()
    }
}
