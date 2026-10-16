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
            SQLiteDatabase.openDatabase(path.path,null,SQLiteDatabase.OPEN_READONLY).use { sql ->
                sql.rawQuery("PRAGMA user_version",null).use { assertTrue(it.moveToFirst());assertEquals(1,it.getInt(0)) }
                sql.rawQuery("SELECT identity_hash FROM room_master_table WHERE id=42",null).use { assertTrue(it.moveToFirst());assertEquals("99b868cf68cf5dffb82edf267ddc78be",it.getString(0)) }
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
