package com.englishtutor.app.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import com.englishtutor.app.data.Correction
import com.englishtutor.app.data.Role
import com.englishtutor.app.data.TutorReply

// Tablas según el "Modelo de datos (Room)" de CLAUDE.md.

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long,
    val scenario: String = "free",
)

@Entity(tableName = "messages", indices = [Index("sessionId")])
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val role: Role,
    val text: String,
    val createdAt: Long,
)

@Entity(tableName = "corrections", indices = [Index("messageId")])
data class CorrectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageId: Long,
    val original: String,
    val better: String,
    val explanation: String,
)

@Entity(tableName = "vocab", indices = [Index(value = ["term"], unique = true)])
data class VocabEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val term: String,
    val meaning: String,
    val example: String,
    val createdAt: Long,
    val timesReviewed: Int = 0,
)

@Dao
abstract class TutorDao {
    @Insert abstract suspend fun insertSession(session: SessionEntity): Long
    @Insert abstract suspend fun insertMessage(message: MessageEntity): Long
    @Insert abstract suspend fun insertCorrections(corrections: List<CorrectionEntity>)

    // term es único: si la palabra ya estaba guardada, no se duplica.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertVocab(vocab: List<VocabEntity>)

    @Query("UPDATE sessions SET endedAt = :endedAt WHERE id = :sessionId")
    abstract suspend fun updateSessionEnd(sessionId: Long, endedAt: Long)

    /** Errores más repetidos (agrupados por la forma correcta); si empatan, los más recientes. */
    @Query(
        "SELECT original, better, explanation FROM corrections " +
            "GROUP BY lower(trim(better)) ORDER BY COUNT(*) DESC, MAX(id) DESC LIMIT :limit"
    )
    abstract suspend fun frequentErrors(limit: Int): List<Correction>

    /** Guarda un turno completo (tu mensaje + correcciones + respuesta + vocabulario): todo o nada. */
    @Transaction
    open suspend fun saveTurn(sessionId: Long, userText: String, reply: TutorReply) {
        val now = System.currentTimeMillis()
        val userMsgId = insertMessage(MessageEntity(sessionId = sessionId, role = Role.USER, text = userText, createdAt = now))
        insertCorrections(reply.corrections.map {
            CorrectionEntity(messageId = userMsgId, original = it.original, better = it.better, explanation = it.explanation)
        })
        insertMessage(MessageEntity(sessionId = sessionId, role = Role.ASSISTANT, text = reply.reply, createdAt = now))
        insertVocab(reply.new_vocab.map {
            VocabEntity(term = it.term.trim(), meaning = it.meaning, example = it.example, createdAt = now)
        })
        updateSessionEnd(sessionId, now)
    }
}

@Database(
    entities = [SessionEntity::class, MessageEntity::class, CorrectionEntity::class, VocabEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class TutorDatabase : RoomDatabase() {
    abstract fun dao(): TutorDao

    companion object {
        fun create(context: Context): TutorDatabase =
            Room.databaseBuilder(context, TutorDatabase::class.java, "tutor.db").build()
    }
}
