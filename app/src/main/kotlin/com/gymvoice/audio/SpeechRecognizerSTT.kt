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

    suspend fun transcribe(): String =
        withContext(Dispatchers.Main) {
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

                        override fun onError(error: Int) {
                            sr.destroy()
                            recognizer = null
                            val msg = "SpeechRecognizer error $error"
                            Log.e("GymVoice", msg)
                            if (cont.isActive) cont.resumeWithException(RuntimeException(msg))
                        }

                        override fun onReadyForSpeech(params: Bundle?) = Unit

                        override fun onBeginningOfSpeech() = Unit

                        override fun onRmsChanged(rmsdB: Float) = Unit

                        override fun onBufferReceived(buffer: ByteArray?) = Unit

                        override fun onEndOfSpeech() = Unit

                        override fun onPartialResults(partialResults: Bundle?) = Unit

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
        recognizer?.stopListening()
    }

    fun close() {
        recognizer?.destroy()
        recognizer = null
    }
}
