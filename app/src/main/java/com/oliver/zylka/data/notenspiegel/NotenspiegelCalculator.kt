package com.oliver.zylka.data.notenspiegel

import kotlin.math.roundToLong

/** One grade's point range within a given Gesamtpunktzahl. */
data class GradeBand(val grade: String, val minPoints: Double, val maxPoints: Double)

/**
 * Turns percentage thresholds into concrete point ranges for a given Gesamtpunktzahl.
 * Bands are contiguous and non-overlapping: each grade's floor is its threshold rounded to
 * the requested [PointsPrecision] (ganze oder halbe Punkte), and its ceiling is the point
 * just below the next-better grade's floor - except the best grade, which always reaches
 * up to the full Gesamtpunktzahl.
 */
object NotenspiegelCalculator {

    fun compute(
        system: GradingSystem,
        thresholds: List<Double>,
        totalPoints: Double,
        precision: PointsPrecision = PointsPrecision.DEFAULT,
    ): List<GradeBand> {
        require(thresholds.size == system.grades.size) {
            "Erwarte ${system.grades.size} Schwellen für ${system.id}, bekam ${thresholds.size}"
        }
        if (totalPoints <= 0.0) return emptyList()

        val step = precision.step
        val floors = thresholds.map { roundToStep(it / 100.0 * totalPoints, step) }
        return system.grades.indices.map { i ->
            val lower = floors[i]
            val upper = if (i == 0) totalPoints else (floors[i - 1] - step).coerceAtLeast(lower)
            GradeBand(grade = system.grades[i], minPoints = lower, maxPoints = upper)
        }
    }

    private fun roundToStep(value: Double, step: Double): Double = (value / step).roundToLong() * step
}
