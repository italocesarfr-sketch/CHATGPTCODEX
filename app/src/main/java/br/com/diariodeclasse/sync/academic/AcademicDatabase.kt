package br.com.diariodeclasse.sync.academic

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName="sync_academic_control")
data class AcademicControl(@PrimaryKey val id:Int=1,val owner:String,val stage:String="PREPARED",
    val enabled:Boolean=false,val cursor:Long=0,val applying:Boolean=false,val message:String="PENDING",val checked:Boolean=false)
@Entity(tableName="sync_academic_records",primaryKeys=["kind","key1","key2"])
data class AcademicState(val kind:String,val key1:String,val key2:String="",val owner:String,
    val localVersion:Long=1,val acknowledged:Long=0,val revision:Long=0,val base:String?=null)
@Entity(tableName="sync_academic_outbox")
data class AcademicPending(@PrimaryKey val id:String,val owner:String,val body:String,val versions:String,
    val createdAt:Long,val attempts:Int=0,val conflict:String?=null)
@Entity(tableName="sync_academic_inbox")
data class AcademicIncoming(@PrimaryKey val sequence:Long,val owner:String,val body:String,val reason:String)
@Entity(tableName="sync_academic_applied")
data class AcademicApplied(@PrimaryKey val sequence:Long,val owner:String,val operation:String,val digest:String)

@Entity(tableName="sync_academic_parts",primaryKeys=["sequence","offset"])
data class AcademicPart(val sequence:Long,val offset:Int,val owner:String,val operation:String,val total:Int,
    val count:Int,val digest:String,val body:String)

@Dao interface AcademicDao {
    @Query("SELECT * FROM sync_academic_control WHERE id=1") suspend fun control():AcademicControl?
    @Query("SELECT * FROM sync_academic_control WHERE id=1") fun watchControl():Flow<AcademicControl?>
    @Upsert suspend fun save(value:AcademicControl)
    @Query("SELECT * FROM sync_academic_records ORDER BY kind,key1,key2") suspend fun states():List<AcademicState>
    @Query("SELECT * FROM sync_academic_records WHERE kind=:kind AND key1=:key1 AND key2=:key2") suspend fun record(kind:String,key1:String,key2:String):AcademicState?
    @Query("SELECT * FROM sync_academic_records ORDER BY kind,key1,key2") fun watchStates():Flow<List<AcademicState>>
    @Upsert suspend fun save(value:AcademicState)
    @Query("SELECT * FROM sync_academic_outbox ORDER BY createdAt,id") suspend fun pending():List<AcademicPending>
    @Query("SELECT * FROM sync_academic_outbox ORDER BY createdAt,id") fun watchPending():Flow<List<AcademicPending>>
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insert(value:AcademicPending)
    @Query("UPDATE sync_academic_outbox SET attempts=attempts+1 WHERE id=:id") suspend fun attempt(id:String)
    @Query("UPDATE sync_academic_outbox SET conflict=:reason WHERE id=:id") suspend fun conflict(id:String,reason:String)
    @Query("DELETE FROM sync_academic_outbox WHERE id=:id") suspend fun acknowledge(id:String)
    @Query("SELECT * FROM sync_academic_inbox ORDER BY sequence") suspend fun incoming():List<AcademicIncoming>
    @Query("SELECT * FROM sync_academic_inbox ORDER BY sequence") fun watchIncoming():Flow<List<AcademicIncoming>>
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insert(value:AcademicIncoming)
    @Query("SELECT * FROM sync_academic_applied WHERE sequence=:sequence") suspend fun applied(sequence:Long):AcademicApplied?
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insert(value:AcademicApplied)
    @Query("SELECT * FROM sync_academic_parts ORDER BY sequence,offset") suspend fun parts():List<AcademicPart>
    @Query("SELECT * FROM sync_academic_parts WHERE body<>'' ORDER BY sequence,offset") suspend fun stagedParts():List<AcademicPart>
    @Query("SELECT * FROM sync_academic_parts WHERE sequence=:sequence AND offset=:offset") suspend fun part(sequence:Long,offset:Int):AcademicPart?
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insert(value:AcademicPart)
    @Query("UPDATE sync_academic_parts SET body='' WHERE sequence=:sequence") suspend fun confirmParts(sequence:Long)
}

fun installAcademicTriggers(db:SupportSQLiteDatabase) {
    // Two distinct key columns, no ambiguous concatenation and no dependency on optional SQLite JSON1.
    for(table in AcademicKind.entries.map{it.wire}) for((event,ref) in listOf("INSERT" to "NEW","UPDATE" to "NEW","DELETE" to "OLD")) {
        val key1=when(table){"terms","class_archives","preserved_histories"->"$ref.classId";"relocations"->"$ref.sourceStudentId";"grades"->"$ref.activityId";"marks"->"$ref.lessonId";"recompositions"->"$ref.studentId";else->"$ref.id"}
        val key2=when(table){"terms"->"CAST($ref.number AS TEXT)";"grades","marks"->"$ref.studentId";"recompositions"->"CAST($ref.term AS TEXT)";else->"''"}
        val stages=when(table){in phaseBKinds->"'PREPARED','ACTIVE','PREPARED_C','ACTIVE_C','PREPARED_D','ACTIVE_D','PREPARED_E','ACTIVE_E'";in phaseCKinds->"'PREPARED_C','ACTIVE_C','PREPARED_D','ACTIVE_D','PREPARED_E','ACTIVE_E'";else->"'PREPARED_D','ACTIVE_D','PREPARED_E','ACTIVE_E'"}
        // Do not rely on OR IGNORE inside a trigger: an outer INSERT/UPDATE OR ABORT
        // overrides it in SQLite. Guard the insert instead; the same transaction's UPDATE
        // increments both new and existing counters, retaining every PK/unique constraint.
        // Replace only our own capture trigger definition; no academic row is modified.
        db.execSQL("DROP TRIGGER IF EXISTS academic_${table}_${event.lowercase()}")
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS academic_${table}_${event.lowercase()} AFTER $event ON $table
            WHEN EXISTS(SELECT 1 FROM sync_academic_control WHERE id=1 AND stage IN ($stages) AND applying=0)
            BEGIN
              INSERT INTO sync_academic_records(kind,key1,key2,owner,localVersion,acknowledged,revision,base)
              SELECT '$table',$key1,$key2,owner,0,0,0,NULL FROM sync_academic_control WHERE id=1
                AND NOT EXISTS(SELECT 1 FROM sync_academic_records r WHERE r.kind='$table' AND r.key1=$key1 AND r.key2=$key2);
              UPDATE sync_academic_records SET localVersion=localVersion+1 WHERE kind='$table' AND key1=$key1 AND key2=$key2;
            END""".trimIndent())
    }
}
val ACADEMIC_MIGRATION_12_13=object:Migration(12,13) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_academic_parts (sequence INTEGER NOT NULL, offset INTEGER NOT NULL, owner TEXT NOT NULL, operation TEXT NOT NULL, total INTEGER NOT NULL, count INTEGER NOT NULL, digest TEXT NOT NULL, body TEXT NOT NULL, PRIMARY KEY(sequence,offset))")
        installAcademicTriggers(db)
    }
}
val ACADEMIC_MIGRATION_11_12=object:Migration(11,12) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_academic_control (id INTEGER NOT NULL, owner TEXT NOT NULL, stage TEXT NOT NULL, enabled INTEGER NOT NULL, cursor INTEGER NOT NULL, applying INTEGER NOT NULL, message TEXT NOT NULL, checked INTEGER NOT NULL, PRIMARY KEY(id))")
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_academic_records (kind TEXT NOT NULL, key1 TEXT NOT NULL, key2 TEXT NOT NULL, owner TEXT NOT NULL, localVersion INTEGER NOT NULL, acknowledged INTEGER NOT NULL, revision INTEGER NOT NULL, base TEXT, PRIMARY KEY(kind,key1,key2))")
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_academic_outbox (id TEXT NOT NULL, owner TEXT NOT NULL, body TEXT NOT NULL, versions TEXT NOT NULL, createdAt INTEGER NOT NULL, attempts INTEGER NOT NULL, conflict TEXT, PRIMARY KEY(id))")
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_academic_inbox (sequence INTEGER NOT NULL, owner TEXT NOT NULL, body TEXT NOT NULL, reason TEXT NOT NULL, PRIMARY KEY(sequence))")
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_academic_applied (sequence INTEGER NOT NULL, owner TEXT NOT NULL, operation TEXT NOT NULL, digest TEXT NOT NULL, PRIMARY KEY(sequence))")
        installAcademicTriggers(db)
    }
}
