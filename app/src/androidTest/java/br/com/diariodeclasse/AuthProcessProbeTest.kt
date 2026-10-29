package br.com.diariodeclasse

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import br.com.diariodeclasse.auth.*
import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class AuthProcessProbeTest {
    @Test fun protectedSessionAndAcademicDataSurviveRebootWithoutAnOnlineBypass():Unit=runBlocking {
        val phase=InstrumentationRegistry.getArguments().getString("authProcessPhase")
        assumeTrue(phase in listOf("write","read"))
        val app=ApplicationProvider.getApplicationContext<DiaryApplication>()
        val context=AuthSandbox(app,File(app.filesDir,"auth-process-probe"))
        val owner=AuthUser(AuthConfig.LEGACY_OWNER_ID,"owner@example.invalid",true,"Proprietário Fictício")
        val expected=File(context.filesDir,"expected-academic.json")
        val online=PrincipalTestClient(owner)
        val offline=object:AuthClient by online {
            override suspend fun verify(session:AuthSession):AuthSession=throw AuthFailure(AuthFailure.Kind.NETWORK,"Sem conexão de teste.")
            override suspend fun refresh(session:AuthSession):AuthSession=throw AuthFailure(AuthFailure.Kind.NETWORK,"Sem conexão de teste.")
        }
        val auth=AuthController(if(phase=="write")online else offline,KeystoreAuthVault(context));auth.restore()
        val manager=AccountWorkspaces(context,auth)
        try {
            if(phase=="write") {
                check(!expected.exists())
                val db=DiaryDatabase.open(context,context.getDatabasePath("diario.db").absolutePath)
                try {
                    val repo=DiaryRepository(db);val c=SchoolClass("auth-process-class",name="Turma Fictícia Reinício",subject="História",year=2026)
                    val s=Student(id="auth-process-student",classId=c.id,name="Estudante Fictícia",number=1,joined="2026-01-01")
                    repo.saveClass(c,"2026-01-01");repo.saveStudent(s);repo.setDayHours(c.id,"2026-08-10",1)
                    val lesson="${c.id}:2026-08-10:1";repo.mark(lesson,s.id,"FJ");repo.setJustification(repo.dao.attendance(lesson).single(),"Atestado médico")
                } finally{db.close()}
                auth.login(owner.email,"Senha-ficticia-2026")
                manager.claim(owner,Uri.fromFile(File(context.cacheDir,"verified-backup.json")),"Backup-Ficticio-2026".toCharArray())
                val workspace=manager.activate(owner);workspace.selection.write(AppSelection(2026,"auth-process-class"))
                expected.writeBytes(workspace.backup.backupBytes())
            } else {
                assertEquals(owner.id,auth.state.value.user?.id);assertTrue(auth.state.value.offline)
                val workspace=manager.activate(owner)
                assertEquals(BackupCodec.decode(expected.readBytes()).snapshot,workspace.backup.snapshot())
                assertEquals("Atestado médico",workspace.backup.snapshot().marks.single().reason)
            }
        } finally{manager.lock()}
    }
}
