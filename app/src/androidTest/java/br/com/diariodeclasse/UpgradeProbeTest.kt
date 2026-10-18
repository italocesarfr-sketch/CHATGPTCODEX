package br.com.diariodeclasse

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Sonda compatível com o APK antigo: usa só Android/JSON, não classes novas de backup. */
@RunWith(AndroidJUnit4::class)
class UpgradeProbeTest {
    @Test fun allSyntheticRowsSurviveInInstalledApp() {
        org.junit.Assume.assumeTrue("Sonda exclusiva do teste de atualização", InstrumentationRegistry.getArguments().getString("upgradeProbe") == "true")
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val expected=JSONObject(instrumentation.context.assets.open("baseline-v1.json").bufferedReader().use { it.readText() })
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> assertFalse(activity.isFinishing) }
            val path=instrumentation.targetContext.getDatabasePath("diario.db")
            @Suppress("DEPRECATION")
            val code=instrumentation.targetContext.packageManager.getPackageInfo("br.com.diariodeclasse",0).versionCode
            val migrated=code>=9
            // Activity RESUMED não significa que a primeira consulta Room já terminou.
            // Aguarda a abertura/migração pelo app, sem abrir Room nem alterar o banco aqui.
            val expectedVersion=if(code>=18) 7 else if(code>=15) 6 else if(code>=14) 5 else if(code>=13) 4 else if(code>=11) 3 else if(migrated) 2 else 1
            val deadline=android.os.SystemClock.uptimeMillis()+10000
            var observedVersion=0
            do {
                SQLiteDatabase.openDatabase(path.path,null,SQLiteDatabase.OPEN_READONLY).use { check ->
                    check.rawQuery("PRAGMA user_version",null).use { cursor ->
                        assertTrue(cursor.moveToFirst());observedVersion=cursor.getInt(0)
                    }
                }
                if(observedVersion!=expectedVersion) Thread.sleep(25)
            } while(observedVersion!=expectedVersion && android.os.SystemClock.uptimeMillis()<deadline)
            assertEquals("A abertura/migração do app deve concluir antes da conferência",expectedVersion,observedVersion)
            SQLiteDatabase.openDatabase(path.path,null,SQLiteDatabase.OPEN_READONLY).use { sql ->
                sql.rawQuery("PRAGMA user_version",null).use { assertTrue(it.moveToFirst());assertEquals(expectedVersion,it.getInt(0)) }
                sql.rawQuery("SELECT identity_hash FROM room_master_table WHERE id=42",null).use { assertTrue(it.moveToFirst());assertEquals(if(code>=18) br.com.diariodeclasse.backup.BackupCodec.IDENTITY else if(code>=15) br.com.diariodeclasse.backup.BackupCodec.V6_IDENTITY else if(code>=14) br.com.diariodeclasse.backup.BackupCodec.V5_IDENTITY else if(code>=13) br.com.diariodeclasse.backup.BackupCodec.V4_IDENTITY else if(code>=11) br.com.diariodeclasse.backup.BackupCodec.V3_IDENTITY else if(migrated) br.com.diariodeclasse.backup.BackupCodec.V2_IDENTITY else "99b868cf68cf5dffb82edf267ddc78be",it.getString(0)) }
                expected.keys().forEach { field ->
                    val rows=expected.getJSONArray(field);val table=if(field=="drafts") "import_drafts" else field
                    sql.rawQuery("SELECT COUNT(*) FROM `$table`",null).use { assertTrue(it.moveToFirst());assertEquals(rows.length(),it.getInt(0)) }
                    val keys=when(field) { "marks" -> listOf("lessonId","studentId");"grades" -> listOf("activityId","studentId");"terms" -> listOf("classId","number");else -> listOf("id") }
                    for(i in 0 until rows.length()) {
                        val row=rows.getJSONObject(i)
                        sql.rawQuery("SELECT * FROM `$table` WHERE "+keys.joinToString(" AND ") { "`$it` = ?" },keys.map { row.get(it).toString() }.toTypedArray()).use { cursor ->
                            assertTrue(cursor.moveToFirst())
                            row.keys().forEach { key ->
                                val index=cursor.getColumnIndexOrThrow(key)
                                when(val value=row.get(key)) {
                                    JSONObject.NULL -> assertTrue(cursor.isNull(index))
                                    is Boolean -> assertEquals(if(value) 1 else 0,cursor.getInt(index))
                                    is Number -> assertEquals(value.toDouble(),cursor.getDouble(index),0.0000001)
                                    else -> assertEquals(value.toString(),cursor.getString(index))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
