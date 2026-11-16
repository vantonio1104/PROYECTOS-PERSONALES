package com.englishtutor.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.englishtutor.app.data.local.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Comprueba que actualizar la app de la base v1 a la v2 conserva tus datos. */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    // Esquema exacto que Room generaba en la versión 1
    private val v1Schema = listOf(
        "CREATE TABLE IF NOT EXISTS `sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `startedAt` INTEGER NOT NULL, `endedAt` INTEGER, `scenario` TEXT NOT NULL)",
        "CREATE TABLE IF NOT EXISTS `messages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, `role` TEXT NOT NULL, `text` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_messages_sessionId` ON `messages` (`sessionId`)",
        "CREATE TABLE IF NOT EXISTS `corrections` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `messageId` INTEGER NOT NULL, `original` TEXT NOT NULL, `better` TEXT NOT NULL, `explanation` TEXT NOT NULL, FOREIGN KEY(`messageId`) REFERENCES `messages`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_corrections_messageId` ON `corrections` (`messageId`)",
        "CREATE TABLE IF NOT EXISTS `vocab` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `term` TEXT NOT NULL COLLATE NOCASE, `meaning` TEXT NOT NULL, `example` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `timesReviewed` INTEGER NOT NULL)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_vocab_term` ON `vocab` (`term`)",
        "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
        "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'dcac8bf61c5a80449d6e8b58ea75231e')",
    )

    @Test
    fun `migrar de v1 a v2 conserva el vocabulario y lo deja fuera de Mis palabras`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = context.getDatabasePath("migration-test.db").apply { parentFile?.mkdirs(); delete() }

        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            v1Schema.forEach(db::execSQL)
            db.execSQL("INSERT INTO vocab (term, meaning, example, createdAt, timesReviewed) VALUES ('gist', 'idea general', '', 1, 2)")
            db.version = 1
        }

        val room = Room.databaseBuilder(context, AppDatabase::class.java, file.absolutePath)
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        val word = room.tutorDao().observeVocab().first().single() // falla si el esquema migrado no coincide
        assertEquals("gist", word.term)
        assertEquals(2, word.timesReviewed)
        assertFalse(word.isMine)
        room.close()
    }
}
