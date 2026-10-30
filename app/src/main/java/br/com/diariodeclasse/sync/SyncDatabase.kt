package br.com.diariodeclasse.sync

import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration
import kotlinx.coroutines.flow.Flow

@Entity(tableName="sync_control")
data class SyncControl(@PrimaryKey val id:Int=1, val owner:String, val enabled:Boolean=false,
    val cursor:Long=0, val applying:Boolean=false, val message:String="", val checked:Boolean=false)
@Entity(tableName="sync_classes")
data class SyncClassState(@PrimaryKey val classId:String, val owner:String, val localVersion:Long=1,
    val acknowledged:Long=0, val remoteRevision:Long=0, val basePayload:String="", val conflict:String?=null)
@Entity(tableName="sync_operations",indices=[Index(value=["classId"],unique=true)])
data class SyncOperation(@PrimaryKey val id:String, val classId:String, val owner:String, val localVersion:Long,
    val baseRevision:Long, val kind:String, val payload:String, val createdAt:Long, val attempts:Int=0)

@Dao interface SyncDao {
    @Query("SELECT * FROM sync_control WHERE id=1") suspend fun control():SyncControl?
    @Query("SELECT * FROM sync_control WHERE id=1") fun watchControl():Flow<SyncControl?>
    @Query("SELECT * FROM sync_classes ORDER BY classId") suspend fun states():List<SyncClassState>
    @Query("SELECT * FROM sync_classes ORDER BY classId") fun watchStates():Flow<List<SyncClassState>>
    @Query("SELECT * FROM sync_classes WHERE classId=:id") suspend fun state(id:String):SyncClassState?
    @Query("SELECT * FROM sync_operations ORDER BY createdAt,id") suspend fun operations():List<SyncOperation>
    @Upsert suspend fun save(value:SyncControl)
    @Upsert suspend fun save(value:SyncClassState)
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insert(value:SyncOperation)
    @Query("UPDATE sync_operations SET attempts=attempts+1 WHERE id=:id") suspend fun attempt(id:String)
    @Query("DELETE FROM sync_operations WHERE id=:id") suspend fun acknowledge(id:String)
}

/** Triggers capture every local path (including direct DAO writes) in its academic transaction. */
fun installSyncTriggers(db:SupportSQLiteDatabase) {
    for((table,key) in listOf("classes" to "id","terms" to "classId")) {
        for((event,reference) in listOf("INSERT" to "NEW","UPDATE" to "NEW","DELETE" to "OLD")) {
            db.execSQL("""CREATE TRIGGER IF NOT EXISTS sync_${table}_${event.lowercase()}
                AFTER $event ON $table WHEN EXISTS(SELECT 1 FROM sync_control WHERE id=1 AND applying=0)
                BEGIN UPDATE sync_classes SET localVersion=localVersion+1
                WHERE classId=$reference.$key; END""".trimIndent())
        }
    }
}
val SYNC_MIGRATION_10_11=object:Migration(10,11) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_control (id INTEGER NOT NULL, owner TEXT NOT NULL, enabled INTEGER NOT NULL, cursor INTEGER NOT NULL, applying INTEGER NOT NULL, message TEXT NOT NULL, checked INTEGER NOT NULL, PRIMARY KEY(id))")
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_classes (classId TEXT NOT NULL, owner TEXT NOT NULL, localVersion INTEGER NOT NULL, acknowledged INTEGER NOT NULL, remoteRevision INTEGER NOT NULL, basePayload TEXT NOT NULL, conflict TEXT, PRIMARY KEY(classId))")
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_operations (id TEXT NOT NULL, classId TEXT NOT NULL, owner TEXT NOT NULL, localVersion INTEGER NOT NULL, baseRevision INTEGER NOT NULL, kind TEXT NOT NULL, payload TEXT NOT NULL, createdAt INTEGER NOT NULL, attempts INTEGER NOT NULL, PRIMARY KEY(id))")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sync_operations_classId ON sync_operations(classId)")
        installSyncTriggers(db)
    }
}
