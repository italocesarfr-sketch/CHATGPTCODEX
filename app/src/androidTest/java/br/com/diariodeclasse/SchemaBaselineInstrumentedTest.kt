package br.com.diariodeclasse

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.DiaryDatabase
import br.com.diariodeclasse.data.MIGRATION_10_11
import br.com.diariodeclasse.data.MIGRATION_9_10
import br.com.diariodeclasse.data.MIGRATION_8_9
import br.com.diariodeclasse.data.MIGRATION_7_8
import br.com.diariodeclasse.data.MIGRATION_6_7
import br.com.diariodeclasse.data.MIGRATION_5_6
import br.com.diariodeclasse.data.MIGRATION_4_5
import br.com.diariodeclasse.data.MIGRATION_3_4
import br.com.diariodeclasse.data.MIGRATION_2_3
import br.com.diariodeclasse.data.MIGRATION_1_2
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SchemaBaselineInstrumentedTest {
    @get:Rule val helper=MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),DiaryDatabase::class.java.canonicalName!!,FrameworkSQLiteOpenHelperFactory())
    @Test fun opensFrozenSchemaV1WithoutDestructiveFallback() = runBlocking {
        val name="frozen-schema-test.db"
        val context=ApplicationProvider.getApplicationContext<Context>();context.deleteDatabase(name)
        helper.createDatabase(name,1).use { sql ->
            sql.execSQL("INSERT INTO classes (id,name,stage,subject,year,shift,schedule,duration,scale,low,high,countJustified,startPresent) VALUES ('frozen','Turma fictícia','Médio','História',2026,'Matutino','0,0,0,0,0,0,0',50,10,5,7,1,0)")
        }
        helper.runMigrationsAndValidate(name,2,true,MIGRATION_1_2).close()
        val db=Room.databaseBuilder(context,DiaryDatabase::class.java,name).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,MIGRATION_7_8,MIGRATION_8_9,MIGRATION_9_10,MIGRATION_10_11).build()
        try {
            val manager=BackupManager(db,FileSafetyBackupStore(java.io.File(context.cacheDir,"schema-test-safety")),2,null)
            assertEquals("frozen",manager.snapshot().classes.single().id)
        } finally { db.close();context.deleteDatabase(name) }
    }
}
