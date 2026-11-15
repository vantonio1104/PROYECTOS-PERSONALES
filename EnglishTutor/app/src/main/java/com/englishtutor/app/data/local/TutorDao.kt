package com.englishtutor.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Consultas a la base de datos. Room genera el código SQL real al compilar. */
@Dao
interface TutorDao {

    // ---- Sesiones ----

    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Query("UPDATE sessions SET endedAt = :endedAt WHERE id = :sessionId")
    suspend fun updateSessionEnd(sessionId: Long, endedAt: Long)

    @Query(
        """SELECT s.*, (SELECT COUNT(*) FROM messages m WHERE m.sessionId = s.id) AS messageCount
           FROM sessions s ORDER BY s.startedAt DESC"""
    )
    fun observeSessionSummaries(): Flow<List<SessionSummary>>

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    fun observeSession(sessionId: Long): Flow<SessionEntity?>

    // Las claves foráneas con CASCADE borran también sus mensajes y correcciones
    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    /** Milisegundos practicados desde `since` (suma de la duración de las sesiones). */
    @Query("SELECT COALESCE(SUM(endedAt - startedAt), 0) FROM sessions WHERE startedAt >= :since AND endedAt IS NOT NULL")
    fun observePracticeMillisSince(since: Long): Flow<Long>

    // ---- Mensajes y correcciones ----

    @Insert
    suspend fun insertMessage(message: MessageEntity): Long

    @Insert
    suspend fun insertCorrections(corrections: List<CorrectionEntity>)

    @Transaction
    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY createdAt, id")
    fun observeMessagesWithCorrections(sessionId: Long): Flow<List<MessageWithCorrections>>

    /** Errores agrupados (sin importar mayúsculas), los más repetidos y recientes primero. */
    @Query(
        """SELECT original, better, explanation, COUNT(*) AS times FROM corrections
           GROUP BY LOWER(TRIM(original)), LOWER(TRIM(better))
           ORDER BY times DESC, MAX(id) DESC LIMIT :limit"""
    )
    fun observeFrequentErrors(limit: Int): Flow<List<FrequentError>>

    // ---- Vocabulario ----

    // IGNORE: si el término ya existe (índice único), no se inserta de nuevo
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertVocab(vocab: List<VocabEntity>)

    @Query("SELECT * FROM vocab ORDER BY createdAt DESC, id DESC")
    fun observeVocab(): Flow<List<VocabEntity>>

    @Query("UPDATE vocab SET timesReviewed = timesReviewed + 1 WHERE id = :id")
    suspend fun markVocabReviewed(id: Long)

    @Query("DELETE FROM vocab WHERE id = :id")
    suspend fun deleteVocab(id: Long)
}
