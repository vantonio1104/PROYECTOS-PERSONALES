package com.englishtutor.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Una sesión de práctica (desde que hablas por primera vez hasta tu último turno). */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long? = null,
    val scenario: String = "free_talk", // los escenarios llegan en la Fase 3
)

/** Un mensaje del chat. role = "user" | "assistant". Si se borra la sesión, se borran sus mensajes. */
@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("sessionId")],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val role: String,
    val text: String,
    val createdAt: Long,
)

/** Una corrección del tutor, ligada al mensaje del usuario que la originó. */
@Entity(
    tableName = "corrections",
    foreignKeys = [ForeignKey(
        entity = MessageEntity::class,
        parentColumns = ["id"],
        childColumns = ["messageId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("messageId")],
)
data class CorrectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageId: Long,
    val original: String,
    val better: String,
    val explanation: String,
)

/** Vocabulario nuevo. "term" es único: si el tutor repite una palabra, no se duplica. */
@Entity(tableName = "vocab", indices = [Index(value = ["term"], unique = true)])
data class VocabEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val term: String,
    val meaning: String,
    val example: String,
    val createdAt: Long,
    val timesReviewed: Int = 0,
)
