package com.oliver.zylka.data.waste

import android.content.Context
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Loads the bundled waste-collection calendar (see README.md for provenance: transcribed
 * from the official Bünde/Langenkamp PDF calendars for 2026/2027). Restabfall is listed
 * for every pickup printed in the source calendar (biweekly), since that's the safe
 * choice if the household's actual rhythm turns out to be the 4-weekly one.
 */
class WasteCalendarRepository(private val context: Context) {

    private var cache: List<WasteEvent>? = null

    suspend fun loadEvents(): List<WasteEvent> = withContext(Dispatchers.IO) {
        cache ?: parse().also { cache = it }
    }

    /** Events on or after [from], sorted chronologically. */
    suspend fun upcomingEvents(from: LocalDate = LocalDate.now()): List<WasteEvent> =
        loadEvents().filter { !it.date.isBefore(from) }

    private fun parse(): List<WasteEvent> {
        val text = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        val root = JSONObject(text)
        val events = root.getJSONArray("events")
        return buildList {
            for (i in 0 until events.length()) {
                val obj = events.getJSONObject(i)
                val date = LocalDate.parse(obj.getString("date"))
                val typesArray: JSONArray = obj.getJSONArray("types")
                val types = buildList {
                    for (t in 0 until typesArray.length()) {
                        add(WasteType.fromId(typesArray.getString(t)))
                    }
                }
                add(WasteEvent(date = date, types = types))
            }
        }.sortedBy { it.date }
    }

    companion object {
        const val ASSET_PATH = "waste/buende_langenkamp.json"
    }
}
