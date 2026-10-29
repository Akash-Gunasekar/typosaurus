package com.example.aikeyboard.core.provider

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ResilientStreamerTest {
    private class Fake(override val id: String, val script: MutableList<() -> List<Any>>) : LlmProvider {
        var calls = 0
        override suspend fun listModels() = emptyList<String>()
        override fun stream(request: ChatRequest): Flow<LlmEvent> = flow {
            calls++
            for (x in script.removeAt(0)()) when (x) {
                is LlmEvent -> emit(x)
                is LlmException -> throw x
            }
        }
    }

    private fun ex(k: LlmException.Kind, retryAfter: Long? = null) = LlmException(k, "x", retryAfterSeconds = retryAfter)
    private fun ok(t: String) = listOf<Any>(LlmEvent.Delta(t), LlmEvent.Done("stop"))
    private val req = { _: String -> ChatRequest("m", "s", "u") }
    private fun streamer(vararg ps: Fake, sleeps: MutableList<Long> = mutableListOf()) =
        ResilientStreamer({ id -> ps.first { it.id == id } }, { sleeps += it })

    @Test fun retriesRateLimitHonouringRetryAfterCappedAt5s() = runTest {
        val a = Fake("a", mutableListOf({ listOf(ex(LlmException.Kind.RateLimit, 30)) }, { ok("hi") }))
        val sleeps = mutableListOf<Long>()
        val out = streamer(a, sleeps = sleeps).stream("a", null, req).toList()
        assertEquals(2, a.calls); assertEquals(listOf(5000L), sleeps)
        assertEquals("hi", (out.first() as LlmEvent.Delta).text)
    }

    @Test fun retriesServerErrorAfterOneSecond() = runTest {
        val a = Fake("a", mutableListOf({ listOf(ex(LlmException.Kind.Server)) }, { ok("x") }))
        val sleeps = mutableListOf<Long>()
        streamer(a, sleeps = sleeps).stream("a", null, req).toList()
        assertEquals(listOf(1000L), sleeps)
    }

    @Test fun fallsBackAfterRetryFails() = runTest {
        val a = Fake("a", mutableListOf({ listOf(ex(LlmException.Kind.Server)) }, { listOf(ex(LlmException.Kind.Server)) }))
        val b = Fake("b", mutableListOf({ ok("from b") }))
        val out = streamer(a, b).stream("a", "b", req).toList()
        assertEquals(2, a.calls); assertEquals(1, b.calls)
        assertEquals("from b", (out.first() as LlmEvent.Delta).text)
    }

    @Test fun authErrorIsNeitherRetriedNorFallenBack() = runTest {
        val a = Fake("a", mutableListOf({ listOf(ex(LlmException.Kind.Auth)) }))
        val b = Fake("b", mutableListOf({ ok("x") }))
        val e = assertFailsWith<LlmException> { streamer(a, b).stream("a", "b", req).toList() }
        assertEquals(LlmException.Kind.Auth, e.kind); assertEquals(1, a.calls); assertEquals(0, b.calls)
    }

    @Test fun neverRetriesAfterTextStreamed() = runTest {
        val a = Fake("a", mutableListOf({ listOf(LlmEvent.Delta("par"), ex(LlmException.Kind.Network)) }, { ok("dup") }))
        assertFailsWith<LlmException> { streamer(a).stream("a", null, req).toList() }
        assertEquals(1, a.calls)
    }

    @Test fun unknownFallbackProviderSurfacesError() = runTest {
        val a = Fake("a", mutableListOf({ listOf(ex(LlmException.Kind.Network)) }))
        val s = ResilientStreamer({ id -> if (id == "a") a else throw LlmException(LlmException.Kind.BadRequest, "Unknown provider") }, {})
        val e = assertFailsWith<LlmException> { s.stream("a", "zzz", req).toList() }
        assertEquals(LlmException.Kind.BadRequest, e.kind)
    }
}
