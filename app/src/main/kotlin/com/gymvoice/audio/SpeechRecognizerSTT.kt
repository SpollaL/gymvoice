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
    private var lastPartial = ""

    suspend fun transcribe(): String =
        withContext(Dispatchers.Main) {
            recognizer?.let {
                it.cancel()
                it.destroy()
            }
            recognizer = null
            userStopped = false
            lastPartial = ""

            suspendCancellableCoroutine { cont ->
                val sr = SpeechRecognizer.createSpeechRecognizer(context)
                recognizer = sr

                sr.setRecognitionListener(
                    object : RecognitionListener {
                        override fun onResults(results: Bundle) {
                            val text =
                                results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                    ?.firstOrNull() ?: ""
                            Log.e("GymVoice", "STT result: \"$text\"")
                            sr.destroy()
                            recognizer = null
                            if (cont.isActive) cont.resume(text)
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            val partial =
                                partialResults
                                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                    ?.firstOrNull() ?: return
                            if (partial.isNotBlank()) lastPartial = partial
                        }

                        override fun onError(error: Int) {
                            val partial = lastPartial
                            sr.destroy()
                            recognizer = null
                            Log.e("GymVoice", "STT error $error userStopped=$userStopped partial=\"$partial\"")
                            if (!cont.isActive) return
                            when {
                                userStopped && partial.isNotBlank() -> cont.resume(partial)
                                userStopped -> cont.resume("")
                                else -> cont.resumeWithException(RuntimeException("SpeechRecognizer error $error"))
                            }
                        }

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

                val intent =
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    }

                sr.startListening(intent)

                cont.invokeOnCancellation {
                    sr.cancel()
                    sr.destroy()
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
