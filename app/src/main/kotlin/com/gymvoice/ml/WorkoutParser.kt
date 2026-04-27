package com.gymvoice.ml

import org.json.JSONObject

data class ParsedWorkout(
    val exercise: String,
    val set: Int?,
    val reps: Int?,
    val weight: Float?,
    val unit: String,
)

object WorkoutParser {
    private val WEIGHT_REGEX = Regex("""(\d+(?:\.\d+)?)\s*(kg|lbs|lb|kilos?|pounds?)""", RegexOption.IGNORE_CASE)
    private val WEIGHT_KEYWORD = Regex("""kg|lbs|lb|kilos?|pounds?""", RegexOption.IGNORE_CASE)
    private val REPS_REGEX = Regex("""(\d+)\s*(reps?|repetitions?)""", RegexOption.IGNORE_CASE)
    private val SET_REGEX = Regex("""(\d+)\s*sets?""", RegexOption.IGNORE_CASE)
    private val STOP_WORDS = setOf("set", "sets", "rep", "reps", "kg", "lbs", "lb", "kilo", "kilos", "pound", "pounds")

    private val KNOWN_EXERCISES =
        listOf(
            "bench press",
            "incline bench press",
            "decline bench press",
            "chest press",
            "squat",
            "back squat",
            "front squat",
            "goblet squat",
            "split squat",
            "bulgarian split squat",
            "deadlift",
            "romanian deadlift",
            "sumo deadlift",
            "overhead press",
            "shoulder press",
            "military press",
            "arnold press",
            "lat pulldown",
            "pullup",
            "chinup",
            "bent over row",
            "cable row",
            "seated row",
            "t-bar row",
            "upright row",
            "hip thrust",
            "glute bridge",
            "leg press",
            "leg extension",
            "leg curl",
            "calf raise",
            "dumbbell curl",
            "barbell curl",
            "hammer curl",
            "preacher curl",
            "concentration curl",
            "tricep pushdown",
            "skull crusher",
            "tricep extension",
            "dips",
            "lateral raise",
            "front raise",
            "face pull",
            "cable fly",
            "chest fly",
            "pushup",
            "plank",
            "crunch",
            "sit up",
            "lunge",
            "step up",
            "box jump",
            "good morning",
            "back extension",
            "incline dumbbell press",
            "shrug",
            "cable curl",
        )

    private fun levenshtein(
        a: String,
        b: String,
    ): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                dp[i][j] =
                    if (a[i - 1] == b[j - 1]) {
                        dp[i - 1][j - 1]
                    } else {
                        1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                    }
            }
        }
        return dp[a.length][b.length]
    }

    private fun looksLikeSentence(name: String): Boolean {
        val lower = name.lowercase()
        return lower.any { it.isDigit() } || STOP_WORDS.any { Regex("\\b$it\\b").containsMatchIn(lower) }
    }

    private fun cleanExerciseName(
        llmExercise: String,
        rawText: String,
    ): String = if (looksLikeSentence(llmExercise)) extractExercise(rawText) else llmExercise

    private fun closestExercise(name: String): String {
        val lower = name.lowercase().trim()
        val best = KNOWN_EXERCISES.minByOrNull { levenshtein(lower, it) } ?: return lower
        val threshold = maxOf(2, (lower.length + 2) / 3)
        return if (levenshtein(lower, best) <= threshold) best else lower
    }

    fun parse(
        llmOutput: String,
        rawText: String,
    ): ParsedWorkout? = tryParseLlmJson(llmOutput, rawText) ?: tryParseRegex(rawText)

    private fun tryParseLlmJson(
        json: String,
        rawText: String,
    ): ParsedWorkout? =
        runCatching {
            val start = json.indexOf('{')
            val end = json.lastIndexOf('}')
            if (start == -1 || end == -1) return null
            val obj = JSONObject(json.substring(start, end + 1))

            val exercise =
                listOf("exercise", "name", "workout", "exercise_name")
                    .firstNotNullOfOrNull { obj.optString(it).takeIf { s -> s.isNotBlank() && s != "null" } }
                    ?: return null

            val set =
                listOf("set", "sets", "set_number", "num_sets")
                    .firstNotNullOfOrNull { key -> obj.optInt(key).takeIf { it > 0 } }

            val reps =
                listOf("reps", "repetitions", "rep", "num_reps", "repetition")
                    .firstNotNullOfOrNull { key -> obj.optInt(key).takeIf { it > 0 } }

            val hasWeightWord = WEIGHT_KEYWORD.containsMatchIn(rawText)
            val weight =
                listOf("weight", "load", "weight_kg", "weight_lbs")
                    .firstNotNullOfOrNull { key ->
                        val v = obj.optDouble(key)
                        val s = obj.optString(key)
                        if (s == "null" || v <= 0) null else v
                    }
                    ?.toFloat()
                    ?.takeIf { hasWeightWord }

            ParsedWorkout(
                exercise = closestExercise(cleanExerciseName(exercise, rawText)),
                set = set,
                reps = reps,
                weight = weight,
                unit = normalizeUnit(obj.optString("unit", "kg")),
            )
        }.getOrNull()

    private fun normalizeUnit(raw: String): String {
        val u = raw.lowercase().trim()
        return when {
            u.startsWith("kil") || u == "k" -> "kg"
            u.startsWith("lb") || u.startsWith("pound") -> "lbs"
            else -> "kg"
        }
    }

    private fun tryParseRegex(text: String): ParsedWorkout? {
        val weightMatch = WEIGHT_REGEX.find(text)
        val repsMatch = REPS_REGEX.find(text)
        if (weightMatch == null && repsMatch == null) return null
        return ParsedWorkout(
            exercise = closestExercise(extractExercise(text)),
            set = SET_REGEX.find(text)?.groupValues?.get(1)?.toIntOrNull(),
            reps = repsMatch?.groupValues?.get(1)?.toIntOrNull(),
            weight = weightMatch?.groupValues?.get(1)?.toFloatOrNull(),
            unit =
                weightMatch?.groupValues?.get(2)?.lowercase()
                    ?.replace(Regex("^lb$"), "lbs") ?: "kg",
        )
    }

    private fun extractExercise(text: String): String {
        val words = text.split(Regex("\\s+"))
        val exercise =
            words
                .takeWhile { word -> !word.first().isDigit() && word.lowercase() !in STOP_WORDS }
                .joinToString(" ")
                .trim()
        return exercise.ifBlank { words.take(2).joinToString(" ") }
    }
}
