package com.duecare.journey.journal

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LegalAssessmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(assessment: LegalAssessment)

    /**
     * Update the worker-side fields ONLY. The harness fields
     * (harnessVerdict, harnessReasoning, controllingStatute,
     * controllingConvention, grepHitsJoined, ragDocsJoined, source)
     * are immutable after creation; if the worker wants to disagree
     * they set workerVerdict + workerNotes, and `source` becomes
     * WORKER_OVERRIDE in a new row that supersedes this one (but
     * we keep both — see [history]).
     */
    @Query("UPDATE legal_assessments SET workerVerdict = :verdict, " +
            "workerNotes = :notes WHERE id = :id")
    suspend fun overrideByWorker(id: String, verdict: AssessmentVerdict, notes: String?)

    @Query("SELECT * FROM legal_assessments WHERE id = :id")
    suspend fun byId(id: String): LegalAssessment?

    @Query("SELECT * FROM legal_assessments WHERE targetKind = :kind " +
            "AND targetId = :id ORDER BY assessedAtMillis DESC")
    suspend fun history(kind: AssessmentTarget, id: String): List<LegalAssessment>

    @Query("SELECT * FROM legal_assessments WHERE targetKind = :kind " +
            "AND targetId = :id ORDER BY assessedAtMillis DESC LIMIT 1")
    suspend fun latest(kind: AssessmentTarget, id: String): LegalAssessment?

    @Query("SELECT * FROM legal_assessments WHERE harnessVerdict = :verdict")
    fun observeByHarnessVerdict(verdict: AssessmentVerdict): Flow<List<LegalAssessment>>

    @Query("DELETE FROM legal_assessments WHERE id = :id")
    suspend fun delete(id: String)
}
