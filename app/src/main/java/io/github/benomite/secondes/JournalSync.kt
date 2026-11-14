package io.github.benomite.secondes

import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The shared journal, offline first: a day is saved on the phone as soon as it is filled, marked pending,
 * and sent to the server when it can be. The server keeps the last write of each day; a pull brings the
 * other members' days. Every change is saved whole: a day always has its clip and its author.
 */
class JournalSync(
    private val local: LocalJournal,
    private val remote: RemoteJournal,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val lock = Any()
    private val syncing = Mutex()
    private val state = MutableStateFlow(local.load())

    /** The journal's days as this phone knows them. */
    val days: StateFlow<Map<LocalDate, JournalDay>> = state.asStateFlow()

    /**
     * Fills [date] with [clip] by [author], replacing whoever filled it before; the day is pending until pushed.
     * False when the day could not be saved on the phone: then nothing changed.
     */
    fun fill(date: LocalDate, clip: SharedClip, author: String): Boolean =
        update { it + (date to JournalDay(date, clip, author, pending = true)) }

    /** What the app does when it opens: send the days filled offline, then bring the other members' days. */
    suspend fun syncOnOpen() {
        pushPending()
        pull()
    }

    /** Sends the pending days. True when none is left pending; false when the server or the phone failed. */
    suspend fun pushPending(): Boolean = syncing.withLock {
        for (day in state.value.values.filter { it.pending }) {
            val stored = try {
                withContext(io) { remote.pushDay(day.copy(pending = false)) }
            } catch (e: IOException) {
                return@withLock false
            }
            // A day filled again while it was being sent stays pending: its newer clip is sent next.
            val saved = update { current ->
                if (current[day.date] == day) current + (day.date to stored.copy(date = day.date, pending = false)) else current
            }
            if (!saved) return@withLock false
        }
        state.value.values.none { it.pending }
    }

    /**
     * Brings the server's days. A day pending here keeps its clip: once pushed it is the later write, which the
     * server keeps. False when the server or the phone failed: then the days here are left as they were.
     */
    suspend fun pull(): Boolean = syncing.withLock {
        val remoteDays = try {
            withContext(io) { remote.fetchDays() }
        } catch (e: IOException) {
            return@withLock false
        }
        update { current ->
            val merged = current.toMutableMap()
            remoteDays.forEach { day -> if (current[day.date]?.pending != true) merged[day.date] = day.copy(pending = false) }
            merged
        }
    }

    /** Saves [change] of the days, whole, then shows it; a save that fails changes nothing. */
    private fun update(change: (Map<LocalDate, JournalDay>) -> Map<LocalDate, JournalDay>): Boolean =
        synchronized(lock) {
            val next = change(state.value)
            if (next == state.value) return true
            try {
                local.save(next)
            } catch (e: IOException) {
                return false
            }
            state.value = next
            true
        }
}
