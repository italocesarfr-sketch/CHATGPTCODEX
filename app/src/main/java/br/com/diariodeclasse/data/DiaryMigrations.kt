package br.com.diariodeclasse.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Aditiva: nenhuma tabela v1, valor ou identidade existente é reescrita. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `recompositions` (`studentId` TEXT NOT NULL, `term` INTEGER NOT NULL, `value` REAL, `date` TEXT NOT NULL, PRIMARY KEY(`studentId`, `term`), FOREIGN KEY(`studentId`) REFERENCES `students`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
    }
}
