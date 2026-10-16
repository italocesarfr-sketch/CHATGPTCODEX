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
        helper.runMigrationsAndValidate(name,1,true).close()
        val db=Room.databaseBuilder(context,DiaryDatabase::class.java,name).build()
        try {
            val manager=BackupManager(db,FileSafetyBackupStore(java.io.File(context.cacheDir,"schema-test-safety")),2,null)
            assertEquals("frozen",manager.snapshot().classes.single().id)
        } finally { db.close();context.deleteDatabase(name) }
    }
}
