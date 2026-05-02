package com.duecare.journey.journal

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PartyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(party: Party)

    @Query("SELECT * FROM parties WHERE id = :id")
    suspend fun byId(id: String): Party?

    @Query("SELECT * FROM parties ORDER BY kind, name")
    fun observeAll(): Flow<List<Party>>

    @Query("SELECT * FROM parties WHERE kind = :kind ORDER BY name")
    fun observeByKind(kind: PartyKind): Flow<List<Party>>

    @Query("SELECT * FROM parties WHERE name LIKE '%' || :q || '%' " +
            "ORDER BY kind, name LIMIT 20")
    suspend fun search(q: String): List<Party>

    @Query("DELETE FROM parties WHERE id = :id")
    suspend fun delete(id: String)
}
