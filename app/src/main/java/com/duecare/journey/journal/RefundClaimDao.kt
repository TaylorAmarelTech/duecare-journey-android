package com.duecare.journey.journal

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RefundClaimDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(claim: RefundClaim)

    @Update
    suspend fun update(claim: RefundClaim)

    @Query("SELECT * FROM refund_claims WHERE id = :id")
    suspend fun byId(id: String): RefundClaim?

    @Query("SELECT * FROM refund_claims ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<RefundClaim>>

    @Query("SELECT * FROM refund_claims WHERE status IN " +
            "('DRAFT', 'FILED', 'IN_REVIEW') ORDER BY createdAtMillis DESC")
    fun observeOpen(): Flow<List<RefundClaim>>

    @Query("SELECT * FROM refund_claims WHERE status = :status " +
            "ORDER BY createdAtMillis DESC")
    fun observeByStatus(status: RefundStatus): Flow<List<RefundClaim>>

    @Query("UPDATE refund_claims SET status = :status, " +
            "filedAtMillis = :filedAtMillis, regulatorCaseNumber = :caseNumber " +
            "WHERE id = :id")
    suspend fun markFiled(
        id: String,
        status: RefundStatus,
        filedAtMillis: Long,
        caseNumber: String?,
    )

    @Query("UPDATE refund_claims SET status = :status, " +
            "amountRecoveredMinorUnits = :amount, " +
            "recoveredAtMillis = :at WHERE id = :id")
    suspend fun markRecovered(
        id: String,
        status: RefundStatus,
        amount: Long,
        at: Long,
    )

    @Query("DELETE FROM refund_claims WHERE id = :id")
    suspend fun delete(id: String)
}
