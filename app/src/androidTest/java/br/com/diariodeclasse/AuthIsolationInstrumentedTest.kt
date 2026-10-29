package br.com.diariodeclasse

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import br.com.diariodeclasse.auth.*
import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

internal class AuthSandbox(base:Context,folder:File?=null):ContextWrapper(base) {
    val root=(folder ?: File(base.cacheDir,"auth-isolation-${UUID.randomUUID()}")).apply{mkdirs()}
    override fun getApplicationContext():Context=this
    override fun getDatabasePath(name:String)=if(File(name).isAbsolute)File(name)else File(root,"databases/$name").also{it.parentFile!!.mkdirs()}
    override fun getFilesDir()=File(root,"files").apply{mkdirs()}
    override fun getCacheDir()=File(root,"cache").apply{mkdirs()}
    override fun getNoBackupFilesDir()=File(root,"no-backup").apply{mkdirs()}
    override fun getSharedPreferences(name:String,mode:Int)=baseContext.getSharedPreferences("${root.name}-$name",mode)
}
class PrincipalTestClient(var principal:AuthUser):AuthClient by AcademicTestClient() {
    private fun fresh()=AuthSession(principal,"not-a-real-access-token","not-a-real-refresh-token",System.currentTimeMillis()+3_600_000,System.currentTimeMillis(),System.currentTimeMillis())
    override suspend fun login(email:String,password:String)=fresh()
    override suspend fun verify(session:AuthSession)=fresh()
    override suspend fun refresh(session:AuthSession)=fresh()
}
class IsolationVault:AuthVault {
    var value=AuthSecrets()
    override fun read()=value
    override fun write(value:AuthSecrets){this.value=value}
}
class AuthIsolationInstrumentedTest {
    private lateinit var base:AuthSandbox
    private lateinit var client:PrincipalTestClient
    private lateinit var auth:AuthController
    private lateinit var vault:IsolationVault
    private lateinit var manager:AccountWorkspaces
    private val owner=AuthUser(AuthConfig.LEGACY_OWNER_ID,"owner@example.invalid",true,"Proprietário Fictício")
    private val other=AuthUser("22222222-2222-4222-8222-222222222222","other@example.invalid",true,"Outra Conta Fictícia")
    private lateinit var before:BackupSnapshot
    private val phrase="Backup-Ficticio-Protegido-2026".toCharArray()
    private var clockOffset=0L
    @Before fun seed():Unit=runBlocking {
        base=AuthSandbox(ApplicationProvider.getApplicationContext())
        val db=DiaryDatabase.open(base,base.getDatabasePath("diario.db").absolutePath)
        try {
            val repo=DiaryRepository(db)
            val c=SchoolClass("auth-source",name="Turma Fictícia Origem",subject="História",year=2026)
            val target=c.copy(id="auth-target",name="Turma Fictícia Destino")
            repo.saveClass(c,"2026-01-01");repo.saveClass(target,"2026-01-01")
            val s=Student(id="auth-student",classId=c.id,name="Estudante Fictícia",number=1,joined="2026-01-01")
            repo.saveStudent(s);repo.setDayHours(c.id,"2026-08-10",3)
            repo.mark("${c.id}:2026-08-10:1",s.id,"FJ")
            repo.setJustification(repo.dao.attendance("${c.id}:2026-08-10:1").single(),"Atestado médico")
            repo.mark("${c.id}:2026-08-10:2",s.id,"P");repo.mark("${c.id}:2026-08-10:3",s.id,"NR")
            val activity=Activity("auth-activity",c.id,"Produção Fictícia",type="Produção textual",date="2026-08-10",term=3,maximum=10.0)
            repo.saveActivity(activity);repo.saveGrade(activity.id,s.id,0.0)
            repo.saveRecomposition(Recomposition(s.id,3,6.0,"2026-08-20"))
            repo.saveLessonRecord(LessonRecord("auth-journal",c.id,"2026-08-10","Título Fictício","Descrição legítima\nPreservada"))
            repo.confirmTimetable(repo.previewTimetable(2026,"2026-01-01",listOf(WeeklySlot(classId=c.id,weekday=1,ordinal=1,className=c.name,subject=c.subject,shift=c.shift))))
            repo.confirmRelocation(repo.previewRelocation(s.id,target.id,null,"2026-09-01"))
            val selection=LocalSelectionStore(base).apply{write(AppSelection(2026,target.id))}
            before=BackupManager(db,FileSafetyBackupStore(File(base.filesDir,"safety-backups")),38,null,selection).snapshot()
        } finally{db.close()}
        client=PrincipalTestClient(owner);vault=IsolationVault();auth=AuthController(client,vault){System.currentTimeMillis()+clockOffset};auth.restore();manager=AccountWorkspaces(base,auth)
    }
    @After fun cleanup():Unit=runBlocking {if(::manager.isInitialized)manager.lock();if(::base.isInitialized)base.root.deleteRecursively();Unit}
    private suspend fun login(who:AuthUser){client.principal=who;auth.login(who.email,"Senha-ficticia-2026")}
    private suspend fun claim():File {
        val file=File(base.cacheDir,"before-link.json")
        manager.claim(owner,Uri.fromFile(file),phrase.copyOf());return file
    }
    @Test fun unauthenticatedAndWrongAccountsNeverOpenTheLegacyDatabase():Unit=runBlocking {
        assertThrows(IllegalStateException::class.java){manager.current()}
        login(other);val own=manager.activate(other)
        assertTrue(own.backup.snapshot().classes.isEmpty());assertFalse(own.legacy)
        assertThrows(IllegalStateException::class.java){runBlocking{manager.claim(other,Uri.fromFile(File(base.cacheDir,"invalid.json")),phrase.copyOf())}}
        assertNull(vault.value.legacyOwner)
    }
    @Test fun ownerClaimCreatesVerifiedEncryptedBackupAndPreservesEveryExistingRow():Unit=runBlocking {
        login(owner);assertTrue(manager.needsClaim(owner))
        assertThrows(IllegalStateException::class.java){runBlocking{manager.activate(owner)}}
        val file=claim();val work=manager.activate(owner)
        assertEquals(owner.id,vault.value.legacyOwner);assertTrue(work.legacy)
        assertEquals(before,work.backup.snapshot())
        assertEquals(before,BackupCodec.decode(AccountBackup.open(file.readBytes(),owner.id,phrase)).snapshot)
        assertEquals("Atestado médico",work.backup.snapshot().marks.first{it.mark=="FJ"}.reason)
        work.database.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}
    }
    @Test fun switchingAccountsSeparatesDatabasesSelectionsBackupsAndPreviouslyOpenHandles():Unit=runBlocking {
        login(owner);claim();val first=manager.activate(owner);assertEquals(before,first.backup.snapshot())
        val encrypted=AccountBackup.seal(first.backup.backupBytes(),owner.id,phrase)
        auth.logout();assertThrows(IllegalStateException::class.java){manager.current()};manager.lock()
        assertFalse(first.database.isOpen)
        login(other);val second=manager.activate(other)
        assertTrue(second.backup.snapshot().classes.isEmpty());assertNull(second.selection.read());assertFalse(second.backup.hasSafetyCopy())
        second.repository.saveClass(SchoolClass("auth-other-class",name="Outra Turma Fictícia",subject="História",year=2027),"2027-01-01")
        second.selection.write(AppSelection(2027,"auth-other-class"));val own=second.backup.snapshot()
        assertThrows(AuthFailure::class.java){AccountBackup.open(encrypted,other.id,phrase)}
        auth.logout();manager.lock();login(owner);assertEquals(before,manager.activate(owner).backup.snapshot())
        auth.logout();manager.lock();login(other);assertEquals(own,manager.activate(other).backup.snapshot())
    }
    @Test fun failedBackupOrCancelledClaimNeverWritesOwnershipOrAcademicData():Unit=runBlocking {
        login(owner);assertTrue(manager.needsClaim(owner));assertNull(vault.value.legacyOwner)
        val impossible=File(base.cacheDir,"directory").apply{mkdirs()}
        assertThrows(Exception::class.java){runBlocking{manager.claim(owner,Uri.fromFile(impossible),phrase.copyOf())}}
        assertNull(vault.value.legacyOwner);assertTrue(manager.needsClaim(owner))
        claim();assertEquals(before,manager.activate(owner).backup.snapshot())
    }
    @Test fun logoutBeforeBindingAndUnconfirmedPrincipalsCannotClaim():Unit=runBlocking {
        login(owner);auth.logout()
        assertThrows(IllegalStateException::class.java){runBlocking{claim()}}
        assertNull(vault.value.legacyOwner)
        assertThrows(IllegalArgumentException::class.java){runBlocking{manager.activate(owner.copy(confirmed=false))}}
        login(owner);claim();assertEquals(before,manager.activate(owner).backup.snapshot())
    }
    @Test fun expiredAdmissionCannotReadOrExportUnclaimedAcademicData():Unit=runBlocking {
        login(owner);clockOffset=AuthPolicy.OFFLINE_MILLIS+1000
        val file=File(base.cacheDir,"before-link.json")
        assertThrows(IllegalStateException::class.java){runBlocking{claim()}}
        assertFalse(file.exists());assertNull(vault.value.legacyOwner)
        clockOffset=0;claim();assertEquals(before,manager.activate(owner).backup.snapshot())
    }
    @Test fun keystoreEncryptsTokensAndKeepsOwnershipAfterLogoutAndProcessStyleRecreation():Unit=runBlocking {
        val protected=KeystoreAuthVault(base)
        val current=AuthController(client,protected);current.restore();current.login(owner.email,"Senha-ficticia-2026")
        current.bindLegacy(owner)
        val bytes=File(base.noBackupFilesDir,"noctua-auth-v1.sealed").readBytes()
        assertFalse(bytes.toString(Charsets.UTF_8).contains("not-a-real-access-token"))
        current.logout();assertNull(protected.read().session);assertEquals(owner.id,protected.read().legacyOwner)
        val reopened=AuthController(client,KeystoreAuthVault(base));reopened.restore();assertNull(reopened.state.value.user)
        reopened.login(owner.email,"Senha-ficticia-2026");assertEquals(owner.id,reopened.state.value.user?.id)
        val token=File(base.noBackupFilesDir,"noctua-auth-v1.sealed");val corrupted=token.readBytes();corrupted[20]=(corrupted[20].toInt() xor 1).toByte();token.writeBytes(corrupted)
        val damaged=AuthController(client,KeystoreAuthVault(base));damaged.restore();assertTrue(damaged.state.value.storageFailure);assertNull(damaged.state.value.user)
    }
    @Test fun exportProviderRejectsLoggedOutAndDifferentAccountUris():Unit=runBlocking {
        val app=ApplicationProvider.getApplicationContext<AcademicTestApplication>()
        val folder=File(app.workspace.cache,"exports-share").apply{mkdirs()}
        val own=File(folder,"noctua-auth-provider.xlsx").apply{writeText("Conteúdo fictício")}
        val alien=File(app.cacheDir,"accounts/aaaaaaaa/exports-share/noctua-alien.xlsx").apply{parentFile!!.mkdirs();writeText("Outra conta fictícia")}
        val uri=androidx.core.content.FileProvider.getUriForFile(app,"${app.packageName}.exports",own)
        val forbidden=androidx.core.content.FileProvider.getUriForFile(app,"${app.packageName}.exports",alien)
        try {
            assertEquals("Conteúdo fictício",app.contentResolver.openInputStream(uri)!!.bufferedReader().use{it.readText()})
            assertThrows(Exception::class.java){app.contentResolver.openInputStream(forbidden)}
            app.auth.logout()
            assertThrows(Exception::class.java){app.contentResolver.openInputStream(uri)}
        } finally{app.restoreFixtureUser();own.delete();alien.delete()}
    }
}
