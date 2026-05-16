package com.gymvoice.ml

import com.gymvoice.data.Exercise

data class ExerciseMatch(
    val exercise: Exercise,
    val confidence: Float,
)

object ExerciseMatcher {
    private const val HIGH_CONFIDENCE_THRESHOLD = 0.6f
    private const val CONTAINS_ALL_BOOST = 0.85f

    fun findTopMatches(
        query: String,
        exercises: List<Exercise>,
        limit: Int = 5,
    ): List<ExerciseMatch> {
        val q = query.lowercase().trim()
        val qTokens = q.split(Regex("\\s+")).filter { it.length > 1 }

        return exercises
            .map { ex ->
                val name = ex.name.lowercase()
                val nameTokens = name.split(Regex("[\\s\\-]+")).filter { it.length > 1 }
                val sc = score(q, qTokens, name, nameTokens)
                Triple(ex, sc, levenshtein(q, name))
            }
            .sortedWith(compareByDescending<Triple<Exercise, Float, Int>> { it.second }.thenBy { it.third })
            .take(limit)
            .map { ExerciseMatch(it.first, it.second) }
    }

    fun isHighConfidence(
        query: String,
        match: ExerciseMatch,
    ): Boolean {
        val tokens = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.size <= 1) return false
        return match.confidence >= HIGH_CONFIDENCE_THRESHOLD
    }

    private fun score(
        q: String,
        qTokens: List<String>,
        name: String,
        nameTokens: List<String>,
    ): Float {
        if (q == name) return 1f

        val levDist = levenshtein(q, name)
        val levScore = 1f - levDist.toFloat() / maxOf(q.length, name.length)

        val qSet = qTokens.toSet()
        val nSet = nameTokens.toSet()
        val unionSize = (qSet + nSet).size
        val intersect = qSet.intersect(nSet).size
        val jaccardScore = if (unionSize == 0) 0f else intersect.toFloat() / unionSize

        val containsAll =
            qTokens.isNotEmpty() &&
                qTokens.all { qt -> nameTokens.any { it == qt || it.contains(qt) } }
        val containsBoost = if (containsAll) CONTAINS_ALL_BOOST else 0f

        return maxOf(levScore, jaccardScore, containsBoost)
    }

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
}
