package com.workoutrec.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Why sync is not working (contracts/sheets-log.md "Errors"). */
enum class FailReason { NETWORK, SPREADSHEET, STRUCTURE, OTHER }

sealed interface SyncPhase {
    data object Idle : SyncPhase
    data object Running : SyncPhase
    data class Failing(val reason: FailReason) : SyncPhase
    data object NeedsSignIn : SyncPhase
}

/** Call 20 values cached per spreadsheet (research R8). */
data class SheetInfoCache(val spreadsheetId: String, val timeZone: String, val logSheetId: Int)

/** The last background sync run, shown while sets wait (quickstart-results.md issue 1). */
data class BackgroundRun(val at: Long, val result: String) {
    companion object {
        fun describe(outcome: SyncOutcome?): String = when (outcome) {
            null -> "error"
            is SyncOutcome.Synced -> if (outcome.wrote) "synced" else "nothing to sync"
            SyncOutcome.NoSpreadsheet -> "no spreadsheet"
            is SyncOutcome.Failed -> when (val phase = outcome.phase) {
                is SyncPhase.Failing -> "failed: ${phase.reason.name}"
                SyncPhase.NeedsSignIn -> "failed: sign in"
                else -> "failed"
            }
        }
    }
}

data class SyncStatus(
    val phase: SyncPhase = SyncPhase.Idle,
    val lastSuccessAt: Long? = null,
    val sheet: SheetInfoCache? = null,
    val background: BackgroundRun? = null,
)

/** Sync status values (data-model.md "Sync status"). */
interface SyncStatusStore {
    val status: Flow<SyncStatus>
    suspend fun current(): SyncStatus = status.first()
    suspend fun setPhase(phase: SyncPhase)
    suspend fun markSuccess(at: Long)
    suspend fun setSheet(sheet: SheetInfoCache?)
    suspend fun clear()
    suspend fun recordBackground(run: BackgroundRun)
}

class DataStoreSyncStatusStore(private val dataStore: DataStore<Preferences>) : SyncStatusStore {

    private val stateKey = stringPreferencesKey("sync.state")
    private val lastSuccessKey = longPreferencesKey("sync.lastSuccessAt")
    private val spreadsheetKey = stringPreferencesKey("sync.spreadsheetId")
    private val timeZoneKey = stringPreferencesKey("sync.timeZone")
    private val logSheetKey = intPreferencesKey("sync.logSheetId")
    private val backgroundAtKey = longPreferencesKey("sync.background.at")
    private val backgroundResultKey = stringPreferencesKey("sync.background.result")

    override val status: Flow<SyncStatus> = dataStore.data.map { prefs ->
        val spreadsheetId = prefs[spreadsheetKey]
        val timeZone = prefs[timeZoneKey]
        val logSheetId = prefs[logSheetKey]
        SyncStatus(
            phase = decode(prefs[stateKey]),
            lastSuccessAt = prefs[lastSuccessKey],
            background = prefs[backgroundAtKey]?.let { at -> BackgroundRun(at, prefs[backgroundResultKey].orEmpty()) },
            sheet = if (spreadsheetId != null && timeZone != null && logSheetId != null) {
                SheetInfoCache(spreadsheetId, timeZone, logSheetId)
            } else {
                null
            },
        )
    }

    override suspend fun setPhase(phase: SyncPhase) {
        dataStore.edit { it[stateKey] = encode(phase) }
    }

    override suspend fun markSuccess(at: Long) {
        dataStore.edit {
            it[stateKey] = encode(SyncPhase.Idle)
            it[lastSuccessKey] = at
        }
    }

    override suspend fun setSheet(sheet: SheetInfoCache?) {
        dataStore.edit {
            if (sheet == null) {
                it.remove(spreadsheetKey)
                it.remove(timeZoneKey)
                it.remove(logSheetKey)
            } else {
                it[spreadsheetKey] = sheet.spreadsheetId
                it[timeZoneKey] = sheet.timeZone
                it[logSheetKey] = sheet.logSheetId
            }
        }
    }

    override suspend fun recordBackground(run: BackgroundRun) {
        dataStore.edit {
            it[backgroundAtKey] = run.at
            it[backgroundResultKey] = run.result
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private fun encode(phase: SyncPhase): String = when (phase) {
        SyncPhase.Idle -> "idle"
        SyncPhase.Running -> "running"
        SyncPhase.NeedsSignIn -> "sign_in"
        is SyncPhase.Failing -> "failing:${phase.reason.name}"
    }

    private fun decode(value: String?): SyncPhase = when {
        value == null || value == "idle" -> SyncPhase.Idle
        value == "running" -> SyncPhase.Idle // a run cannot survive the process
        value == "sign_in" -> SyncPhase.NeedsSignIn
        value.startsWith("failing:") ->
            SyncPhase.Failing(runCatching { FailReason.valueOf(value.removePrefix("failing:")) }.getOrDefault(FailReason.OTHER))
        else -> SyncPhase.Idle
    }
}
