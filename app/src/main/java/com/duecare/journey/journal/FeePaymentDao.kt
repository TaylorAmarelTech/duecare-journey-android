package com.duecare.journey.journal

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FeePaymentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(payment: FeePayment)

    @Update
    suspend fun update(payment: FeePayment)

    @Query("SELECT * FROM fee_payments WHERE id = :id")
    suspend fun byId(id: String): FeePayment?

    @Query("SELECT * FROM fee_payments ORDER BY paidAtMillis DESC")
    fun observeAll(): Flow<List<FeePayment>>

    @Query("SELECT * FROM fee_payments WHERE paidToId = :partyId " +
            "ORDER BY paidAtMillis DESC")
    fun observeByPayee(partyId: String): Flow<List<FeePayment>>

    @Query("SELECT * FROM fee_payments WHERE stage = :stage " +
            "ORDER BY paidAtMillis DESC")
    fun observeByStage(stage: JourneyStage): Flow<List<FeePayment>>

    @Query("SELECT * FROM fee_payments WHERE refundClaimId IS NULL " +
            "AND legalAssessmentId IS NOT NULL ORDER BY paidAtMillis DESC")
    fun observeRecoverable(): Flow<List<FeePayment>>

    @Query("SELECT SUM(amountMinorUnits) FROM fee_payments " +
            "WHERE currency = :currency")
    suspend fun totalPaidInCurrency(currency: String): Long?

    @Query("DELETE FROM fee_payments WHERE id = :id")
    suspend fun delete(id: String)
}
