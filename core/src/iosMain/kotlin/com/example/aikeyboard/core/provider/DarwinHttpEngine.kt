package com.example.aikeyboard.core.provider

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

actual fun defaultHttpEngine(): HttpEngine = DarwinHttpEngine()

/** NSURLSession through Ktor's Darwin engine, with the same timeouts as the Android client. */
class DarwinHttpEngine(
    private val client: HttpClient = HttpClient(Darwin) {
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 30_000 // gap between SSE chunks; the runner enforces the total limit
        }
        expectSuccess = false
    },
) : HttpEngine {

    override suspend fun send(url: String, headers: Map<String, String>, body: String?): String = guard {
        val res: HttpResponse = if (body == null) {
            client.get(url) { headers.forEach { (k, v) -> header(k, v) } }
        } else {
            client.post(url) {
                headers.forEach { (k, v) -> header(k, v) }
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
        val text = res.bodyAsText()
        if (!res.status.isSuccess()) throw httpError(res.status.value, text, res.headers["Retry-After"])
        text
    }

    override fun sse(url: String, headers: Map<String, String>, body: String): Flow<String> = flow {
        guard {
            client.preparePost(url) {
                headers.forEach { (k, v) -> header(k, v) }
                contentType(ContentType.Application.Json)
                setBody(body)
            }.execute { res ->
                if (!res.status.isSuccess()) throw httpError(res.status.value, res.bodyAsText(), res.headers["Retry-After"])
                val channel = res.bodyAsChannel()
                while (true) {
                    val line = channel.readUTF8Line() ?: break
                    if (line.startsWith("data:")) emit(line.removePrefix("data:").trim())
                }
            }
        }
    }

    /** Maps transport failures to [LlmException]; cancellation and provider errors pass through. */
    private inline fun <T> guard(block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: LlmException) {
        throw e
    } catch (e: HttpRequestTimeoutException) {
        throw LlmException(LlmException.Kind.Timeout, "Timed out", cause = e)
    } catch (e: Exception) {
        throw LlmException(LlmException.Kind.Network, "Network error", cause = e)
    }
}
