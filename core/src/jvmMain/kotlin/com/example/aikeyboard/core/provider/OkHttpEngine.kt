package com.example.aikeyboard.core.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit

actual fun defaultHttpEngine(): HttpEngine = OkHttpEngine()

class OkHttpEngine(private val client: OkHttpClient = defaultClient()) : HttpEngine {

    override suspend fun send(url: String, headers: Map<String, String>, body: String?): String =
        execute(request(url, headers, body)).use { res ->
            val text = withContext(Dispatchers.IO) { res.body?.string().orEmpty() }
            if (!res.isSuccessful) throw httpError(res.code, text, res.header("Retry-After"))
            text
        }

    override fun sse(url: String, headers: Map<String, String>, body: String): Flow<String> = flow {
        val call = client.newCall(request(url, headers, body))
        val handle = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
        try {
            val response = try {
                call.execute()
            } catch (e: IOException) {
                currentCoroutineContext().ensureActive()
                throw e.toLlm()
            }
            response.use { res ->
                if (!res.isSuccessful) throw httpError(res.code, res.body?.string().orEmpty(), res.header("Retry-After"))
                val source = res.body?.source() ?: throw LlmException(LlmException.Kind.Server, "Empty response")
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = try {
                        source.readUtf8Line()
                    } catch (e: IOException) {
                        if (!currentCoroutineContext().isActive) throw e
                        throw e.toLlm()
                    } ?: break
                    if (line.startsWith("data:")) emit(line.removePrefix("data:").trim())
                }
            }
        } finally {
            handle?.dispose()
        }
    }.flowOn(Dispatchers.IO)

    private fun request(url: String, headers: Map<String, String>, body: String?): Request =
        Request.Builder().url(url)
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .apply { if (body == null) get() else post(body.toRequestBody(JSON)) }
            .build()

    /** Blocking execute on IO that cancels the OkHttp call when the coroutine is cancelled. */
    private suspend fun execute(request: Request): Response = withContext(Dispatchers.IO) {
        val call = client.newCall(request)
        val handle = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
        try {
            call.execute()
        } catch (e: IOException) {
            currentCoroutineContext().ensureActive()
            throw e.toLlm()
        } finally {
            handle?.dispose()
        }
    }

    companion object {
        private val JSON = "application/json".toMediaType()

        /**
         * readTimeout bounds the gap between SSE chunks. There is deliberately no callTimeout:
         * it would also cap long streams; the total limit is enforced by ActionRunner.
         */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}

internal fun IOException.toLlm(): LlmException = when (this) {
    is InterruptedIOException -> LlmException(LlmException.Kind.Timeout, "Timed out", cause = this)
    else -> LlmException(LlmException.Kind.Network, "Network error", cause = this)
}
