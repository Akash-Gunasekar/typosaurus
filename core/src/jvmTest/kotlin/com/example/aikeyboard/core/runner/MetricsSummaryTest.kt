package com.example.aikeyboard.core.runner

import com.example.aikeyboard.core.provider.LlmException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class MetricsSummaryTest {
    private fun MetricsSummary.add(vararg e: MetricEvent) = e.fold(this) { acc, ev -> acc.record(ev, 1_000) }

    @Test fun aggregatesLatencyPerProviderModel() {
        val s = MetricsSummary().add(
            MetricEvent.Request("groq", "llama", "fix", 100, 400),
            MetricEvent.Request("groq", "llama", "fix", 300, 800),
            MetricEvent.Request("groq", "llama", "fix", null, 50, LlmException.Kind.RateLimit, 429),
        )
        val m = s.models.getValue("groq · llama")
        assertEquals(2, m.ok)
        assertEquals(3, m.requests)
        assertEquals(200, m.firstToken.avgMs)
        assertEquals(600, m.total.avgMs)
        assertEquals(800, m.total.maxMs)
        assertEquals(mapOf("RateLimit 429" to 1), m.errors)
        assertEquals(1_000, s.sinceMs)
    }

    @Test fun replaceSuccessAndUndoRatesPerAction() {
        val s = MetricsSummary().add(
            MetricEvent.Replace("fix", true), MetricEvent.Replace("fix", true),
            MetricEvent.Replace("fix", true), MetricEvent.Replace("fix", false),
            MetricEvent.Undo("fix"),
        )
        val a = s.actions.getValue("fix")
        assertEquals(0.75, a.replaceSuccessRate)
        assertEquals(1.0 / 3, a.undoRate)
        assertEquals(null, ActionStats().undoRate)
    }

    @Test fun coldStartAndJsonRoundTrip() {
        val s = MetricsSummary().add(MetricEvent.ColdStart(250), MetricEvent.ColdStart(350))
        assertEquals(300, s.coldStart.avgMs)
        val json = Json { ignoreUnknownKeys = true }
        assertEquals(s, json.decodeFromString(MetricsSummary.serializer(), json.encodeToString(MetricsSummary.serializer(), s)))
    }
}
