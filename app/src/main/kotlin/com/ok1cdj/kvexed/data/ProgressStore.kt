package com.ok1cdj.kvexed.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ok1cdj.kvexed.core.Json
import kotlinx.coroutines.flow.first

/** Per-level record: solved, best (lowest) move count, and whether a hint was used. */
data class LevelStat(
    val solved: Boolean = false,
    val bestMoves: Int = 0,
    val hintUsed: Boolean = false,
)

/**
 * Progress for one pack: per-level stats plus a single resumable in-progress
 * game (board string + move count) so a killed app resumes exactly.
 */
data class PackProgress(
    val levels: Map<Int, LevelStat> = emptyMap(),
    val resumeLevel: Int? = null,
    val resumeBoard: String? = null,
    val resumeMoves: Int = 0,
) {
    val solvedCount: Int get() = levels.values.count { it.solved }
}

/** Whole-app progress: which pack/level to continue, and each pack's progress. */
data class Progress(
    val lastPackId: String? = null,
    val lastLevel: Int = 0,
    val packs: Map<String, PackProgress> = emptyMap(),
)

/** User-configurable options. */
data class Settings(
    val hideSolve: Boolean = true,   // the Solve/spoiler button is hidden by default
    val haptics: Boolean = true,     // buzz on each completed move
)

private val Context.dataStore by preferencesDataStore(name = "progress")

/**
 * Reads and writes [Progress] via Preferences DataStore. One JSON string per
 * pack (key `pack:<id>`) plus a small global blob (`state`) — all hand-serialized
 * with the core [Json], no Gson/Room. The app keeps progress in memory and calls
 * [save] on pause, so this is only touched at start and stop.
 */
class ProgressStore(private val context: Context) {

    suspend fun load(): Progress {
        val prefs = context.dataStore.data.first()
        val global = prefs[GLOBAL]?.let { Json.parseObject(it) } ?: emptyMap()
        val packs = HashMap<String, PackProgress>()
        for ((key, value) in prefs.asMap()) {
            val name = key.name
            if (name.startsWith(PACK_PREFIX) && value is String) {
                packs[name.removePrefix(PACK_PREFIX)] = deserializePack(value)
            }
        }
        return Progress(
            lastPackId = global["lastPackId"] as? String,
            lastLevel = (global["lastLevel"] as? Double)?.toInt() ?: 0,
            packs = packs,
        )
    }

    suspend fun loadSettings(): Settings {
        val prefs = context.dataStore.data.first()
        val m = prefs[SETTINGS]?.let { Json.parseObject(it) } ?: return Settings()
        return Settings(
            hideSolve = m["hideSolve"] as? Boolean ?: true,
            haptics = m["haptics"] as? Boolean ?: true,
        )
    }

    suspend fun saveSettings(s: Settings) {
        context.dataStore.edit { prefs ->
            prefs[SETTINGS] = Json.stringify(mapOf("hideSolve" to s.hideSolve, "haptics" to s.haptics))
        }
    }

    suspend fun save(progress: Progress) {
        context.dataStore.edit { prefs ->
            prefs[GLOBAL] = Json.stringify(
                mapOf("lastPackId" to progress.lastPackId, "lastLevel" to progress.lastLevel)
            )
            for ((id, pp) in progress.packs) {
                prefs[stringPreferencesKey("$PACK_PREFIX$id")] = serializePack(pp)
            }
        }
    }

    private fun serializePack(pp: PackProgress): String {
        val levels = pp.levels.mapKeys { it.key.toString() }.mapValues { (_, s) ->
            mapOf("s" to s.solved, "b" to s.bestMoves, "h" to s.hintUsed)
        }
        return Json.stringify(
            mapOf(
                "levels" to levels,
                "resumeLevel" to pp.resumeLevel,
                "resumeBoard" to pp.resumeBoard,
                "resumeMoves" to pp.resumeMoves,
            )
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun deserializePack(text: String): PackProgress {
        val m = Json.parseObject(text)
        val levelsRaw = (m["levels"] as? Map<String, Any?>) ?: emptyMap()
        val levels = levelsRaw.entries.associate { (k, v) ->
            val s = v as Map<String, Any?>
            k.toInt() to LevelStat(
                solved = s["s"] as? Boolean ?: false,
                bestMoves = (s["b"] as? Double)?.toInt() ?: 0,
                hintUsed = s["h"] as? Boolean ?: false,
            )
        }
        return PackProgress(
            levels = levels,
            resumeLevel = (m["resumeLevel"] as? Double)?.toInt(),
            resumeBoard = m["resumeBoard"] as? String,
            resumeMoves = (m["resumeMoves"] as? Double)?.toInt() ?: 0,
        )
    }

    private companion object {
        val GLOBAL = stringPreferencesKey("state")
        val SETTINGS = stringPreferencesKey("settings")
        const val PACK_PREFIX = "pack:"
    }
}
