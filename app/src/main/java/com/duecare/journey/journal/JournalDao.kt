package com.duecare.journey.journal

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: JournalEntry)

    @Query("DELETE FROM journal_entries WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM journal_entries")
    suspend fun deleteAll()

    @Query("SELECT * FROM journal_entries ORDER BY timestampMillis DESC")
    fun observeAll(): Flow<List<JournalEntry>>

    @Query("SELECT * FROM journal_entries " +
            "ORDER BY timestampMillis DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<JournalEntry>

    @Query("SELECT * FROM journal_entries WHERE stage = :stage " +
            "ORDER BY timestampMillis DESC")
    fun observeByStage(stage: JourneyStage): Flow<List<JournalEntry>>

    @Query("SELECT DISTINCT stage FROM journal_entries " +
            "ORDER BY timestampMillis DESC LIMIT 1")
    suspend fun currentStage(): JourneyStage?

    @Query("SELECT COUNT(*) FROM journal_entries")
    suspend fun count(): Int
}
