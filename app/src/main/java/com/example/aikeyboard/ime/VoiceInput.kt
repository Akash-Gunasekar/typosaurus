package com.example.aikeyboard.ime

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Voice typing through the phone's speech recognition service (usually Google's). Must be used from the
 * main thread. Audio goes to that service, not to the AI provider; nothing is recorded by this app.
 */
class VoiceInput(private val context: Context, private val onText: (String) -> Unit) {

    sealed interface State {
        data object Idle : State
        data class Listening(val partial: String = "", val level: Float = 0f) : State
        data class Error(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state
    private var recognizer: SpeechRecognizer? = null

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _state.value = State.Error("No speech recognition service on this phone")
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(listener)
            startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName),
            )
        }
        _state.value = State.Listening()
    }

    /** Stop listening; the final result still arrives and is typed. */
    fun stop() { recognizer?.stopListening() }

    /** Abandon the session without typing anything. */
    fun cancel() {
        recognizer?.cancel()
        _state.value = State.Idle
    }

    fun dismissError() { if (_state.value is State.Error) _state.value = State.Idle }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
        _state.value = State.Idle
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onRmsChanged(rmsdB: Float) {
            val s = _state.value as? State.Listening ?: return
            _state.value = s.copy(level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val s = _state.value as? State.Listening ?: return
            partialResults.best()?.let { _state.value = s.copy(partial = it) }
        }

        override fun onResults(results: Bundle?) {
            _state.value = State.Idle
            results.best()?.takeIf { it.isNotBlank() }?.let(onText)
        }

        override fun onError(error: Int) {
            _state.value = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> State.Error("Didn't catch that — tap the mic to try again")
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> State.Error("Allow the microphone in Settings › Keyboard")
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> State.Error("Network problem — voice typing needs a connection")
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> State.Error("Voice typing is busy — try again")
                SpeechRecognizer.ERROR_CLIENT -> State.Idle // our own cancel/stop
                else -> State.Error("Voice typing failed — try again")
            }
        }

        private fun Bundle?.best(): String? =
            this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
    }
}
