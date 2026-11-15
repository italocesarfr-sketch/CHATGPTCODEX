package br.com.diariodeclasse

import android.net.Uri
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import br.com.diariodeclasse.auth.*
import br.com.diariodeclasse.backup.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Seed calls only APIs present in the original c40. No Auth or academic network client. */
class C40BoundUpgradeProbeTest {
    @Test fun boundOwnerAndFrozenV1ConflictSurviveUpdate():Unit=runBlocking {
        val phase=InstrumentationRegistry.getArguments().getString("c40BoundPhase")
        assumeTrue(phase in listOf("offline","seed","check"))
        check(Build.HARDWARE in setOf("ranchu","goldfish"))
        val app=ApplicationProvider.getApplicationContext<DiaryApplication>()
        val connectivity=app.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        assertTrue(connectivity.allNetworks.none{connectivity.getNetworkCapabilities(it)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)==true})

        if(phase=="offline")return@runBlocking
        val owner=AuthUser(AuthConfig.LEGACY_OWNER_ID,"owner@example.invalid",true,"Proprietário fictício")
        val auth=AuthController(PrincipalTestClient(owner),KeystoreAuthVault(app));auth.restore()
        val manager=AccountWorkspaces(app,auth)
        val reference=File(app.filesDir,"c40-bound-reference.json")
        val password="C40-Fictitious-Backup-2026".toCharArray()
        try {
            auth.login(owner.email,"Senha-ficticia-2026")
            if(phase=="seed") {
                assertTrue(manager.needsClaim(owner))
                val copy=File(app.cacheDir,"c40-before-binding.enc")
                manager.claim(owner,Uri.fromFile(copy),password.copyOf());copy.delete()
            } else {
                assertEquals(owner.id,auth.legacyOwner());assertFalse(manager.needsClaim(owner))
            }
            val w=manager.activate(owner)
            val sql=w.database.openHelper.writableDatabase
            if(phase=="seed") {
                val school=w.backup.snapshot().classes.first().id
                // Synthetic unresolved v1 conflict + immutable pending operation. Never enrolled/enabled.
                sql.execSQL("INSERT OR REPLACE INTO sync_control(id,enabled,owner,cursor,applying,checked,message) VALUES(1,0,?,0,0,0,'CONFLICT')",arrayOf(owner.id))
                sql.execSQL("INSERT OR REPLACE INTO sync_classes(classId,owner,remoteRevision,localVersion,acknowledged,basePayload,conflict) VALUES(?,?,1,2,1,'{}',?)",arrayOf(school,owner.id,"{\"syntheticConflict\":true}"))
                sql.execSQL("INSERT INTO sync_operations(id,classId,owner,localVersion,baseRevision,kind,payload,createdAt,attempts) VALUES('c40-frozen',?,?,2,1,'UPSERT','frozen-synthetic-payload',1,2)",arrayOf(school,owner.id))
                reference.writeBytes(w.backup.backupBytes())
            } else {
                assertEquals(0,app.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)
                assertFalse(br.com.diariodeclasse.sync.academic.academicTestEligible(w))
                assertEquals(BackupCodec.decode(reference.readBytes()).snapshot,w.backup.snapshot())
                assertNull(w.database.academicDao().control()) // Release never silently enrolls E.
            }
            sql.query("SELECT enabled,message FROM sync_control WHERE id=1").use{assertTrue(it.moveToFirst());assertEquals(0,it.getInt(0));assertEquals("CONFLICT",it.getString(1))}
            sql.query("SELECT remoteRevision,localVersion,acknowledged,conflict FROM sync_classes WHERE owner=?",arrayOf(owner.id)).use{
                assertTrue(it.moveToFirst());assertEquals(1,it.getLong(0));assertEquals(2,it.getLong(1));assertEquals(1,it.getLong(2));assertEquals("{\"syntheticConflict\":true}",it.getString(3))
            }
            sql.query("SELECT payload,attempts FROM sync_operations WHERE id='c40-frozen'").use{assertTrue(it.moveToFirst());assertEquals("frozen-synthetic-payload",it.getString(0));assertEquals(2,it.getInt(1))}
            auth.logout();manager.lock()
            val other=AuthUser("44444444-4444-4444-8444-444444444444","other@example.invalid",true,"Outra conta fictícia")
            val client=PrincipalTestClient(other);val otherAuth=AuthController(client,KeystoreAuthVault(app));otherAuth.restore();otherAuth.login(other.email,"Senha-ficticia-2026")
            val isolated=AccountWorkspaces(app,otherAuth)
            try{assertTrue(isolated.activate(other).backup.snapshot().classes.isEmpty())}finally{otherAuth.logout();isolated.lock()}
            assertEquals(owner.id,auth.legacyOwner())
        }finally{password.fill('\u0000');auth.logout();manager.lock()}
    }
}
