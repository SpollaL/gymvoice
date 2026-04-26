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
    private val SET_REGEX = Regex("""set\s*(\d+)""", RegexOption.IGNORE_CASE)

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
            val exercise = obj.optString("exercise").takeIf { it.isNotBlank() } ?: return null
            val hasWeightWord = WEIGHT_KEYWORD.containsMatchIn(rawText)
            ParsedWorkout(
                exercise = exercise,
                set = obj.optInt("set").takeIf { it > 0 },
                reps = obj.optInt("reps").takeIf { it > 0 },
                weight = obj.optDouble("weight").takeIf { it > 0 && hasWeightWord }?.toFloat(),
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
            exercise = text.split(" ").take(2).joinToString(" "),
            set = SET_REGEX.find(text)?.groupValues?.get(1)?.toIntOrNull(),
            reps = repsMatch?.groupValues?.get(1)?.toIntOrNull(),
            weight = weightMatch?.groupValues?.get(1)?.toFloatOrNull(),
            unit =
                weightMatch?.groupValues?.get(2)?.lowercase()
                    ?.replace(Regex("^lb$"), "lbs") ?: "kg",
        )
    }
}
