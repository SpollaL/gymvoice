package com.gymvoice.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SpeechRecognizerSTT(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null
    private var userStopped = false

    suspend fun transcribe(): String =
        withContext(Dispatchers.Main) {
            recognizer?.let {
                it.cancel()
                it.destroy()
            }
            recognizer = null
            userStopped = false

            suspendCancellableCoroutine { cont ->
                val accumulated = StringBuilder()

                val intent =
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
                    }

                fun startSession() {
                    if (!cont.isActive) return
                    val sr = SpeechRecognizer.createSpeechRecognizer(context)
                    recognizer = sr

                    sr.setRecognitionListener(
                        object : RecognitionListener {
                            override fun onResults(results: Bundle) {
                                val text =
                                    results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                        ?.firstOrNull().orEmpty()
                                Log.d("GymVoice", "STT segment: \"$text\" userStopped=$userStopped")
                                sr.destroy()
                                recognizer = null
                                if (text.isNotBlank()) {
                                    if (accumulated.isNotEmpty()) accumulated.append(" ")
                                    accumulated.append(text)
                                }
                                if (userStopped || !cont.isActive) {
                                    if (cont.isActive) cont.resume(accumulated.toString())
                                } else {
                                    startSession()
                                }
                            }

                            override fun onError(error: Int) {
                                Log.d(
                                    "GymVoice",
                                    "STT error $error userStopped=$userStopped accumulated=\"$accumulated\"",
                                )
                                sr.destroy()
                                recognizer = null
                                if (!cont.isActive) return
                                when {
                                    userStopped -> cont.resume(accumulated.toString())
                                    // engine cut off on silence — restart transparently
                                    error == SpeechRecognizer.ERROR_NO_MATCH ||
                                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> startSession()
                                    // real error: return whatever we have so far, or throw
                                    accumulated.isNotEmpty() -> cont.resume(accumulated.toString())
                                    else -> cont.resumeWithException(RuntimeException("SpeechRecognizer error $error"))
                                }
                            }

                            override fun onPartialResults(partialResults: Bundle?) = Unit

                            override fun onReadyForSpeech(params: Bundle?) = Unit

                            override fun onBeginningOfSpeech() = Unit

                            override fun onRmsChanged(rmsdB: Float) = Unit

                            override fun onBufferReceived(buffer: ByteArray?) = Unit

                            override fun onEndOfSpeech() = Unit

                            override fun onEvent(
                                eventType: Int,
                                params: Bundle?,
                            ) = Unit
                        },
                    )

                    sr.startListening(intent)
                }

                startSession()

                cont.invokeOnCancellation {
                    recognizer?.cancel()
                    recognizer?.destroy()
                    recognizer = null
                }
            }
        }

    fun stop() {
        userStopped = true
        recognizer?.stopListening()
    }

    fun close() {
        recognizer?.destroy()
        recognizer = null
    }
}
