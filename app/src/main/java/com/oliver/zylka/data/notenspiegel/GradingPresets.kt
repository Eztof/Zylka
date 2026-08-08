package com.oliver.zylka.data.notenspiegel

/**
 * Default Prozent-Schwellen (Mindestprozent je Notenstufe, beste zuerst), passend zu
 * [GradingSystem.grades]. Beide Tabellen sind gängige, breit referenzierte Standardschlüssel
 * - keine gesetzlich für Klassenarbeiten vorgeschriebenen Werte (NRW kennt für die
 * Sekundarstufe I bewusst keinen landesweit einheitlichen Schlüssel; verbindlich ist das,
 * was die jeweilige Fachkonferenz beschließt). Beide Voreinstellungen lassen sich in den
 * Einstellungen der App vollständig anpassen.
 *
 * - Sechs-Stufen-Schlüssel 92/81/67/50/30/0 %: der verbreitete "IHK-Notenschlüssel", der
 *   auch an vielen NRW-Schulen als Ausgangspunkt für Klassenarbeiten verwendet wird.
 * - 15-Punkte-Schlüssel: die bundesweit übliche Punkte-Prozent-Zuordnung der gymnasialen
 *   Oberstufe (95/90/85/80/…/0 %), wie sie u. a. in den NRW-Vorgaben zur Notenbildung im
 *   Zentralabitur als Orientierung dient.
 */
object GradingPresets {

    val SECHS_STUFEN_DEFAULT: List<Double> = listOf(92.0, 81.0, 67.0, 50.0, 30.0, 0.0)

    val FUNFZEHN_PUNKTE_DEFAULT: List<Double> = listOf(
        95.0, 90.0, 85.0, 80.0, 75.0, 70.0, 65.0, 60.0,
        55.0, 50.0, 45.0, 40.0, 33.0, 27.0, 20.0, 0.0,
    )

    fun defaultFor(system: GradingSystem): List<Double> = when (system) {
        GradingSystem.SECHS_STUFEN -> SECHS_STUFEN_DEFAULT
        GradingSystem.FUNFZEHN_PUNKTE -> FUNFZEHN_PUNKTE_DEFAULT
    }
}
