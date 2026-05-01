package com.gymvoice.ml

import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GemmaInference {
    private var engine: Engine? = null

    suspend fun initialize() =
        withContext(Dispatchers.Default) {
            val config = EngineConfig(modelPath = MODEL_PATH)
            val e = Engine(config)
            e.initialize()
            engine = e
        }

    suspend fun extractWorkoutInfo(normalizedText: String): String =
        withContext(Dispatchers.Default) {
            val e = engine ?: error("GemmaInference not initialized")
            val prompt = "$SYSTEM_PROMPT\nInput: \"$normalizedText\"\nOutput:"
            val sb = StringBuilder()
            e.createConversation().use { conv ->
                conv.sendMessageAsync(prompt).collect { token -> sb.append(token) }
            }
            sb.toString().trim()
        }

    fun close() {
        engine?.close()
        engine = null
    }

    companion object {
        private const val MODEL_PATH = "/data/local/tmp/gemma-3-270m-it-int8.litertlm"

        private const val SYSTEM_PROMPT =
            "Input: \"bench press 10 reps 3 sets 80 kg\"\n" +
                "Output: {\"exercise\":\"bench press\",\"set\":3,\"reps\":10,\"weight\":80,\"unit\":\"kg\"}\n" +
                "Input: \"squat 1 set 5 reps 100 kilos\"\n" +
                "Output: {\"exercise\":\"squat\",\"set\":1,\"reps\":5,\"weight\":100,\"unit\":\"kg\"}\n" +
                "Input: \"deadlift 2 sets 185 lbs\"\n" +
                "Output: {\"exercise\":\"deadlift\",\"set\":2,\"reps\":null,\"weight\":185,\"unit\":\"lbs\"}\n" +
                "Input: \"pullups 3 sets 8 reps\"\n" +
                "Output: {\"exercise\":\"pullups\",\"set\":3,\"reps\":8,\"weight\":null,\"unit\":null}\n" +
                "Input: \"lat pulldown set 2 12 reps 60 kg\"\n" +
                "Output: {\"exercise\":\"lat pulldown\",\"set\":2,\"reps\":12,\"weight\":60,\"unit\":\"kg\"}"

        private val NUMBER_WORDS =
            mapOf(
                "once" to "1",
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

        private val EXERCISE_CORRECTIONS =
            listOf(
                // lat pulldown phonetic variants
                Regex("\\blight\\s+pull\\s*down\\b", RegexOption.IGNORE_CASE) to "lat pulldown",
                Regex("\\blat\\s+pull\\s*down\\b", RegexOption.IGNORE_CASE) to "lat pulldown",
                Regex("\\blap\\s+pull\\s*down\\b", RegexOption.IGNORE_CASE) to "lat pulldown",
                // hip thrust phonetic variants (cheap/chip/hit trust/ist)
                Regex("\\b(?:cheap|chip|hit|heat|heap)\\s+tr(?:ust|ist)\\b", RegexOption.IGNORE_CASE) to "hip thrust",
                Regex("\\bhip\\s+tr(?:ust|ist)\\b", RegexOption.IGNORE_CASE) to "hip thrust",
                // Romanian deadlift before generic deadlift fix
                Regex("\\bromanian\\s+dead\\s*lift\\b", RegexOption.IGNORE_CASE) to "Romanian deadlift",
                Regex("\\brdl\\b", RegexOption.IGNORE_CASE) to "Romanian deadlift",
                // compound word fixes
                Regex("\\bdead\\s+lift\\b", RegexOption.IGNORE_CASE) to "deadlift",
                Regex("\\bdumb\\s+bell\\b", RegexOption.IGNORE_CASE) to "dumbbell",
                Regex("\\bbar\\s+bell\\b", RegexOption.IGNORE_CASE) to "barbell",
                Regex("\\bover\\s+head\\b", RegexOption.IGNORE_CASE) to "overhead",
                Regex("\\bpull\\s+up\\b", RegexOption.IGNORE_CASE) to "pullup",
                Regex("\\bpush\\s+up\\b", RegexOption.IGNORE_CASE) to "pushup",
                Regex("\\bchin\\s+up\\b", RegexOption.IGNORE_CASE) to "chinup",
                // skull crusher mishearings
                Regex("\\bschool\\s+crush(?:er)?\\b", RegexOption.IGNORE_CASE) to "skull crusher",
                Regex("\\bskull\\s+(?:rush(?:er)?|crash(?:er)?)\\b", RegexOption.IGNORE_CASE) to "skull crusher",
                // face pull mishearings
                Regex("\\b(?:faith|phase|fate)\\s+pull\\b", RegexOption.IGNORE_CASE) to "face pull",
                // lateral raise mishearings
                Regex("\\blateral\\s+r(?:aise|ace)\\b", RegexOption.IGNORE_CASE) to "lateral raise",
                Regex("\\blatter\\s*al\\s+raise\\b", RegexOption.IGNORE_CASE) to "lateral raise",
                // glute bridge mishearings (glue/gloo/gloot bridge)
                Regex("\\bgl(?:oo|ue|u)t?e?\\s+bridge\\b", RegexOption.IGNORE_CASE) to "glute bridge",
                // calf raise mishearings
                Regex("\\bcalf\\s+r(?:aise|ace)\\b", RegexOption.IGNORE_CASE) to "calf raise",
                // row variants
                Regex("\\bbent\\s+over\\s+ro(?:w|le|ll)\\b", RegexOption.IGNORE_CASE) to "bent over row",
                Regex("\\bse(?:ated|eded)\\s+row\\b", RegexOption.IGNORE_CASE) to "seated row",
                Regex("\\btable\\s+row\\b", RegexOption.IGNORE_CASE) to "cable row",
                Regex("\\btable\\s+fl(?:y|ies)\\b", RegexOption.IGNORE_CASE) to "cable fly",
                // tricep pushdown mishearings
                Regex("\\btri\\s*cep\\s+push\\s*down\\b", RegexOption.IGNORE_CASE) to "tricep pushdown",
                Regex("\\btry\\s+step\\s+push\\s*down\\b", RegexOption.IGNORE_CASE) to "tricep pushdown",
                // hammer curl mishearings
                Regex("\\bhamm?er\\s+curl\\b", RegexOption.IGNORE_CASE) to "hammer curl",
                Regex("\\bhammer\\s+girl\\b", RegexOption.IGNORE_CASE) to "hammer curl",
                Regex("\\bhamper\\s+curl\\b", RegexOption.IGNORE_CASE) to "hammer curl",
                // incline/decline spacing
                Regex("\\bin\\s+cline\\b", RegexOption.IGNORE_CASE) to "incline",
                Regex("\\bde\\s+cline\\b", RegexOption.IGNORE_CASE) to "decline",
                // other mishearings
                Regex("\\bshould\\s+her\\s+press\\b", RegexOption.IGNORE_CASE) to "shoulder press",
                Regex("\\bsplit\\s+squat\\b", RegexOption.IGNORE_CASE) to "split squat",
                Regex("\\bjust\\s+press\\b", RegexOption.IGNORE_CASE) to "chest press",
            )

        fun normalize(
            text: String,
            userCorrections: Map<String, String> = emptyMap(),
        ): String {
            val withUserFixes =
                userCorrections.entries.fold(text) { acc, (fragment, fix) ->
                    acc.replace(Regex("\\b${Regex.escape(fragment)}\\b", RegexOption.IGNORE_CASE), fix)
                }
            val withExercises =
                EXERCISE_CORRECTIONS.fold(withUserFixes) { acc, (regex, fix) ->
                    regex.replace(acc, fix)
                }
            return NUMBER_WORDS.entries.fold(withExercises) { acc, (word, digit) ->
                acc.replace(Regex("\\b$word\\b", RegexOption.IGNORE_CASE), digit)
            }
        }
    }
}
