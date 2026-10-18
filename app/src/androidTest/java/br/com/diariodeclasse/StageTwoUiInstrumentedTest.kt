package br.com.diariodeclasse

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.diariodeclasse.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class StageTwoUiInstrumentedTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private lateinit var app:DiaryApplication
    private var previous:AppSelection?=null
    private val createdIds=mutableListOf<String>()
    private val a=SchoolClass(id="ui-s2-a",name="Turma Fictícia A",subject="História",year=2026)
    private val b=a.copy(id="ui-s2-b",name="Turma Fictícia B",subject="Geografia")
    private val old=a.copy(id="ui-s2-old",subject="História de 2025",year=2025)
    private val student=Student(id="ui-s2-student",classId=a.id,name="Estudante Fictício Alfa",number=1,joined="2026-01-01")
    private val activity=Activity(id="ui-s2-activity",classId=a.id,name="Atividade Fictícia de Teste",date="2026-03-10",term=1,maximum=10.0)
    @Before fun setup() = runBlocking {
        app=compose.activity.application as DiaryApplication
        previous=app.selectionStore.read()
        listOf(a,b,old).forEach { app.repository.saveClass(it,"${it.year}-01-01") }
        app.repository.saveStudent(student);app.repository.saveActivity(activity)
        app.selectionStore.write(AppSelection(2026,a.id))
        compose.waitUntil(10000) { compose.onAllNodesWithText(a.name).fetchSemanticsNodes().isNotEmpty() }
    }
    @After fun cleanup() = runBlocking {
        // Remove somente fixtures com IDs exclusivos; nunca limpa o banco inteiro.
        val sql=app.database.openHelper.writableDatabase
        app.database.runInTransaction {
            for(id in listOf(a.id,b.id,old.id)+createdIds) {
                sql.execSQL("DELETE FROM marks WHERE lessonId IN (SELECT id FROM lessons WHERE classId=?)",arrayOf(id))
                sql.execSQL("DELETE FROM grades WHERE activityId IN (SELECT id FROM activities WHERE classId=?)",arrayOf(id))
                for(table in listOf("import_drafts","activities","lessons","terms","students")) sql.execSQL("DELETE FROM $table WHERE classId=?",arrayOf(id))
                sql.execSQL("DELETE FROM classes WHERE id=?",arrayOf(id))
            }
        }
        app.selectionStore.write(previous)
    }
    private fun waitText(text:String) {
        compose.waitUntil(10000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun shot(name:String) {
        val folder=File(app.filesDir,"etapa2-screenshots");folder.mkdirs()
        File(folder,"$name.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun fourPagesShareSelectorPersistYearAndSurviveRecreation() {
        compose.onNodeWithText("+ Nova Turma").assertIsDisplayed()
        compose.onNodeWithText("Editar Turma").assertIsDisplayed();shot("01-turmas")
        compose.onNodeWithTag("nav-1").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Bimestres").fetchSemanticsNodes().isNotEmpty() }
        waitText("Mês do ano letivo 2026")
        shot("02-chamada")
        compose.onNodeWithTag("frequency-mode-Bimestre").performClick()
        compose.onNodeWithText("4º bim.").performClick()
        compose.onNodeWithTag("term-frequency").assertExists()
        compose.onNodeWithTag("nav-2").performClick()
        waitText("+ Criar atividade")
        compose.onNodeWithText("+ Criar atividade").assertIsDisplayed();shot("03-atividades")
        compose.onNodeWithTag("nav-3").performClick()
        waitText("Boletim do 4º bimestre")
        compose.onNodeWithText("Boletim do 4º bimestre").assertExists();shot("04-relatorios")
        compose.onNodeWithTag("year-selector").performClick()
        compose.onNodeWithText("Ano letivo 2025").performClick()
        compose.waitUntil(5000) { app.selectionStore.read()==AppSelection(2025,old.id) }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("class-selector").assertIsDisplayed()
        compose.onNodeWithText("História de 2025").assertIsDisplayed()
        assertEquals(AppSelection(2025,old.id),LocalSelectionStore(app).read())
        compose.onAllNodesWithText("Importar alunos por fotografia").assertCountEquals(0)
    }
    @Test fun gradeStaysWithOriginalClassAndInvalidEditWarnsBeforeLeaving() = runBlocking {
        compose.onNodeWithTag("nav-2").performClick()
        waitText("+ Criar atividade")
        compose.onNodeWithText("1º bim.").performClick()
        waitText(activity.name)
        compose.onNodeWithText(activity.name).performClick()
        compose.onNodeWithText("Nota (0 a 10,00)").performTextReplacement("7,5")
        compose.waitUntil(5000) { runBlocking { app.repository.dao.activityGrades(activity.id).firstOrNull()?.value==7.5 } }
        compose.onNodeWithText("Nota (0 a 10,00)").performTextReplacement("12")
        compose.activityRule.scenario.recreate()
        waitText("Valor inválido — não salvo")
        compose.onNodeWithTag("class-selector").performClick()
        compose.onNodeWithText(b.name).performClick()
        compose.onNodeWithText("Sair sem salvar o valor inválido?").assertIsDisplayed()
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithText("Valor inválido — não salvo").assertIsDisplayed()
        compose.onNodeWithTag("class-selector").performClick()
        compose.onNodeWithText(b.name).performClick()
        compose.onNodeWithText("Confirmar").performClick()
        compose.waitUntil(5000) { app.selectionStore.read()?.classId==b.id }
        assertEquals(7.5,app.repository.dao.activityGrades(activity.id).single().value!!,0.0)
        assertTrue(app.repository.dao.roster(b.id).isEmpty())
        waitText("+ Criar atividade")
        compose.onNodeWithText("+ Criar atividade").assertIsDisplayed()
        Unit
    }
    @Test fun createWithoutWeeklyScheduleThenAddAndEditStudent() = runBlocking {
        compose.onNodeWithText("+ Nova Turma").performClick()
        compose.onNodeWithText("Nome da turma").performTextInput("Nova Turma Fictícia UI")
        compose.onNodeWithText("Disciplina").performTextInput("História Fictícia UI")
        compose.onNodeWithText("Segunda").assertDoesNotExist()
        compose.onNodeWithText("Salvar turma").performScrollTo().performClick()
        compose.waitUntil(10000) { runBlocking { app.database.backupDao().classes().any { it.name=="Nova Turma Fictícia UI" } } }
        val created=app.database.backupDao().classes().single { it.name=="Nova Turma Fictícia UI" }
        createdIds.add(created.id)
        assertEquals("0,0,0,0,0,0,0",created.schedule)
        waitText("+ Adicionar aluno")
        compose.onNodeWithText("+ Adicionar aluno").performClick()
        compose.onNodeWithText("Nome completo").performTextInput("Estudante Fictício Novo")
        compose.onNodeWithText("Ingresso (dd/mm/aaaa)").assertTextContains("01/01/2026")
        compose.onNodeWithText("Ingresso (dd/mm/aaaa)").performTextReplacement("01/02/2026")
        compose.onNodeWithText("Salvar aluno").performScrollTo().performClick()
        compose.waitUntil(5000) { runBlocking { app.repository.dao.roster(created.id).size==1 } }
        val first=app.repository.dao.roster(created.id).single()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Cadastrar aluno").fetchSemanticsNodes().isEmpty() }
        waitText("Editar")
        compose.onNodeWithText("Editar").performScrollTo().performClick()
        compose.onNodeWithText("Nome completo").performTextReplacement("Estudante Fictício Editado")
        compose.onNodeWithText("Salvar aluno").performScrollTo().performClick()
        compose.waitUntil(5000) { runBlocking { app.repository.dao.student(first.id)?.name=="Estudante Fictício Editado" } }
        assertEquals(first.personId,app.repository.dao.student(first.id)!!.personId)
        assertEquals("2026-02-01",app.repository.dao.student(first.id)!!.joined)
    }
    @Test fun editingClassNamePreservesExactHiddenSettings() = runBlocking {
        val precise=a.copy(subject="Disciplina Fictícia Precisa",scale=10.125,low=4.875,high=6.995,duration=45,schedule="0,0,2,1,0,0,0",countJustified=false,startPresent=true)
        app.repository.saveClass(precise,"2026-01-01")
        waitText(precise.subject)
        compose.onNodeWithText("Editar Turma").performClick()
        compose.onNodeWithText("Nome da turma").performTextReplacement("Turma Fictícia Editada UI")
        compose.onNodeWithText("Salvar turma").performScrollTo().performClick()
        compose.waitUntil(5000) { runBlocking { app.repository.dao.schoolClass(a.id)?.name=="Turma Fictícia Editada UI" } }
        assertEquals(precise.copy(name="Turma Fictícia Editada UI"),app.repository.dao.schoolClass(a.id))
    }
    @Test fun noNetworkCameraOrPhotoAccessIsRequested() {
        val permissions = app.packageManager.getPackageInfo(app.packageName,android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toSet()
        assertFalse("android.permission.INTERNET" in permissions)
        assertFalse("android.permission.CAMERA" in permissions)
        assertFalse("android.permission.READ_MEDIA_IMAGES" in permissions)
        assertFalse("android.permission.READ_EXTERNAL_STORAGE" in permissions)
    }
    @Test fun pasteRequiresReviewPersistsOnCancelAndConfirms() = runBlocking {
        compose.onNodeWithText("Gerenciar alunos").performScrollTo().performClick()
        waitText("Colar lista")
        compose.onNodeWithText("Colar lista").performClick()
        compose.onNodeWithText("Um nome por linha (número opcional)").performTextInput("18 Estudante Fictício Beta")
        compose.onNodeWithText("Preparar conferência").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Conferir importação").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("import-review-list").performScrollToNode(hasText("Confirmar cadastro de 1 aluno"))
        compose.onNodeWithText("Confirmar cadastro de 1 aluno").assertIsNotEnabled()
        assertEquals(1,app.repository.dao.roster(a.id).size)
        compose.onNodeWithTag("import-review-list").performScrollToNode(hasText("Voltar e manter rascunhos"))
        compose.onNodeWithText("Voltar e manter rascunhos").performClick()
        compose.onNodeWithText("Conferir 1 nome em rascunho").assertExists()
        assertEquals(18,app.repository.dao.draftRows(a.id).single().number)
        assertEquals(1,app.repository.dao.roster(a.id).size)
        compose.onNodeWithText("Conferir 1 nome em rascunho").performClick()
        waitText("Conferir importação")
        compose.onNodeWithText("Ingresso dos novos alunos (dd/mm/aaaa)").assertTextContains("01/01/2026")
        compose.onNodeWithTag("import-review-list").performScrollToNode(isToggleable())
        compose.onNode(isToggleable()).performClick()
        compose.waitUntil(5000) { runBlocking { app.repository.dao.draftRows(a.id).single().reviewed } }
        shot("05-conferencia")
        compose.onNodeWithTag("import-review-list").performScrollToNode(hasText("Confirmar cadastro de 1 aluno"))
        compose.waitUntil(5000) { compose.onAllNodes(hasText("Confirmar cadastro de 1 aluno") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Confirmar cadastro de 1 aluno").performClick()
        compose.onNodeWithText("Confirmar").performClick()
        compose.waitUntil(5000) { runBlocking { app.repository.dao.roster(a.id).size==2 } }
        assertEquals(18,app.repository.dao.roster(a.id).single { it.name=="Estudante Fictício Beta" }.number)
        assertEquals("2026-01-01",app.repository.dao.roster(a.id).single { it.name=="Estudante Fictício Beta" }.joined)
        assertTrue(app.repository.dao.draftRows(a.id).isEmpty())
    }
}
