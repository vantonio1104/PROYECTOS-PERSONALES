package com.englishtutor.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Consultas a la base de datos. Room genera el código SQL real al compilar. */
@Dao
interface TutorDao {

    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Query("UPDATE sessions SET endedAt = :endedAt WHERE id = :sessionId")
    suspend fun updateSessionEnd(sessionId: Long, endedAt: Long)

    @Insert
    suspend fun insertMessage(message: MessageEntity): Long

    @Insert
    suspend fun insertCorrections(corrections: List<CorrectionEntity>)

    // IGNORE: si el término ya existe (índice único), no se inserta de nuevo
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertVocab(vocab: List<VocabEntity>)

    // Lecturas para las pantallas de Historial y Vocabulario (próximos pasos)
    @Query("SELECT * FROM sessions ORDER BY startedAt DESC")
    fun observeSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY createdAt")
    fun observeMessages(sessionId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM vocab ORDER BY createdAt DESC")
    fun observeVocab(): Flow<List<VocabEntity>>
}
