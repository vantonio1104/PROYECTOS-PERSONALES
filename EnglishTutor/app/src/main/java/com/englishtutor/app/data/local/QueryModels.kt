package com.englishtutor.app.data.local

import androidx.room.Embedded
import androidx.room.Relation

/** Una sesión con su cantidad de mensajes (para la lista del Historial). */
data class SessionSummary(
    @Embedded val session: SessionEntity,
    val messageCount: Int,
)

/** Un mensaje con sus correcciones (para ver una sesión antigua). */
data class MessageWithCorrections(
    @Embedded val message: MessageEntity,
    @Relation(parentColumn = "id", entityColumn = "messageId")
    val corrections: List<CorrectionEntity>,
)

/** Un error agrupado: cuántas veces lo cometí. */
data class FrequentError(
    val original: String,
    val better: String,
    val explanation: String,
    val times: Int,
)
