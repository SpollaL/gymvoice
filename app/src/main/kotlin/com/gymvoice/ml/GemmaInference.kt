package com.gymvoice.ml

import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GemmaInference {
    companion object {
        private const val MODEL_PATH = "/data/local/tmp/gemma-3-270m-it-int8.litertlm"

        // Few-shot examples teach Gemma word order: N reps → reps=N, N sets → set=N
        private const val SYSTEM_PROMPT =
            "Extract workout data. Reply ONLY with valid JSON.\n" +
                "Schema: {\"exercise\": string, \"set\": int|null, \"reps\": int|null,\n" +
                "         \"weight\": float|null, \"unit\": \"kg\"|\"lbs\"|null}\n" +
                "The number before \"reps\" is reps. The number before \"set\" is set.\n" +
                "The number before \"kg\"/\"lbs\"/\"kilos\" is weight.\n" +
                "Example 1: \"bench press 10 reps 3 sets 80 kg\"\n" +
                "  → {\"exercise\":\"bench press\",\"set\":3,\"reps\":10,\"weight\":80,\"unit\":\"kg\"}\n" +
                "Example 2: \"squat 1 set 5 reps 100 kilos\"\n" +
                "  → {\"exercise\":\"squat\",\"set\":1,\"reps\":5,\"weight\":100,\"unit\":\"kg\"}\n" +
                "Example 3: \"deadlift 2 sets 185 lbs\"\n" +
                "  → {\"exercise\":\"deadlift\",\"set\":2,\"reps\":null,\"weight\":185,\"unit\":\"lbs\"}"
    }

    private var engine: Engine? = null

    suspend fun initialize() =
        withContext(Dispatchers.Default) {
            val config = EngineConfig(modelPath = MODEL_PATH)
            val e = Engine(config)
            e.initialize()
            engine = e
        }

    suspend fun extractWorkoutInfo(transcribedText: String): String =
        withContext(Dispatchers.Default) {
            val e = engine ?: error("GemmaInference not initialized")
            val normalized = normalizeNumbers(transcribedText)
            val prompt = "$SYSTEM_PROMPT\n\nInput: \"$normalized\"\nOutput JSON:"
            val sb = StringBuilder()
            e.createConversation().use { conv ->
                conv.sendMessageAsync(prompt).collect { token -> sb.append(token) }
            }
            sb.toString().trim()
        }

    private fun normalizeNumbers(text: String): String {
        val words =
            mapOf(
                "one" to "1",
                "two" to "2",
                "three" to "3",
                "four" to "4",
                "five" to "5",
                "six" to "6",
                "seven" to "7",
                "eight" to "8",
                "nine" to "9",
                "ten" to "10",
                "eleven" to "11",
                "twelve" to "12",
                "fifteen" to "15",
                "twenty" to "20",
                "thirty" to "30",
                "forty" to "40",
                "fifty" to "50",
                "hundred" to "100",
            )
        return words.entries.fold(text) { acc, (word, digit) ->
            acc.replace(Regex("\\b$word\\b", RegexOption.IGNORE_CASE), digit)
        }
    }

    fun close() {
        engine?.close()
        engine = null
    }
}
