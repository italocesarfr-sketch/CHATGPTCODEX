package br.com.diariodeclasse.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Aditiva: nenhuma tabela v1, valor ou identidade existente é reescrita. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `recompositions` (`studentId` TEXT NOT NULL, `term` INTEGER NOT NULL, `value` REAL, `date` TEXT NOT NULL, PRIMARY KEY(`studentId`, `term`), FOREIGN KEY(`studentId`) REFERENCES `students`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
    }
}

/** Aditiva: as nove tabelas distribuídas na 0.6.1 permanecem intactas. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `relocations` (`sourceStudentId` TEXT NOT NULL, `targetStudentId` TEXT, `effectiveDate` TEXT NOT NULL, `externalTarget` TEXT, PRIMARY KEY(`sourceStudentId`), FOREIGN KEY(`sourceStudentId`) REFERENCES `students`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`targetStudentId`) REFERENCES `students`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_relocations_targetStudentId` ON `relocations` (`targetStudentId`)")
    }
}

/** Aditiva: somente preferências de exportação; as dez tabelas escolares permanecem intactas. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `export_rows` (`id` TEXT NOT NULL, `classId` TEXT NOT NULL, `studentId` TEXT, `position` INTEGER NOT NULL, `manualHidden` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`classId`) REFERENCES `classes`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`studentId`) REFERENCES `students`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_export_rows_classId_position` ON `export_rows` (`classId`, `position`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_export_rows_studentId` ON `export_rows` (`studentId`)")
    }
}
