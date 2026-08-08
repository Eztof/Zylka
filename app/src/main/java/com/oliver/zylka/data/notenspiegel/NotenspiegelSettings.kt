package com.oliver.zylka.data.notenspiegel

/** A user's saved (or default) percentage thresholds for both grading systems. */
data class NotenspiegelSettings(
    val sechsStufenThresholds: List<Double> = GradingPresets.SECHS_STUFEN_DEFAULT,
    val funfzehnPunkteThresholds: List<Double> = GradingPresets.FUNFZEHN_PUNKTE_DEFAULT,
) {
    fun thresholdsFor(system: GradingSystem): List<Double> = when (system) {
        GradingSystem.SECHS_STUFEN -> sechsStufenThresholds
        GradingSystem.FUNFZEHN_PUNKTE -> funfzehnPunkteThresholds
    }

    fun withThresholds(system: GradingSystem, thresholds: List<Double>): NotenspiegelSettings = when (system) {
        GradingSystem.SECHS_STUFEN -> copy(sechsStufenThresholds = thresholds)
        GradingSystem.FUNFZEHN_PUNKTE -> copy(funfzehnPunkteThresholds = thresholds)
    }
}
