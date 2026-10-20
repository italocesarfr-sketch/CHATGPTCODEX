package br.com.diariodeclasse

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import br.com.diariodeclasse.backup.BackupDialog
import br.com.diariodeclasse.backup.diarySnapshot
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.domain.CsvRoster
import br.com.diariodeclasse.ui.*
import br.com.diariodeclasse.ui.theme.NoctuaTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import java.io.File

/** Varia largura e escala sem mudar preferências do aparelho nem a tipografia do app. */
class PostTwelveLayoutInstrumentedTest {
    @get:Rule val compose=createComposeRule()
    private lateinit var app:DiaryApplication
    private val store=androidx.lifecycle.ViewModelStore()
    private lateinit var vm:DiaryViewModel
    private lateinit var data:DiarySnapshot
    private val c=SchoolClass("ux12-layout",name="Turma Fictícia Layout",subject="História",year=2026)
    private var width by mutableStateOf(320)
    private var scale by mutableStateOf(1f)
    private var screen by mutableStateOf("report")
    @Before fun setup()=runBlocking {
        app=ApplicationProvider.getApplicationContext();vm=DiaryViewModel(app);store.put("ux12-layout",vm)
        app.repository.saveClass(c,"2026-01-01")
        val student=Student("ux12-layout-student","ux12-layout-person",c.id,"Estudante Fictício de Nome Extenso para Conferência",1,"2026-01-01")
        app.repository.saveStudent(student)
        val activity=Activity("ux12-layout-activity",c.id,"Avaliação Fictícia",date="2026-08-28",term=3,maximum=10.0)
        app.repository.saveActivity(activity);app.repository.saveGrade(activity.id,student.id,0.0)
        app.repository.appendDrafts(c.id,listOf(CsvRoster.Row("Estudante Fictício Importado",2)))
        data=app.backupManager.snapshot().diarySnapshot()
        compose.setContent {
            val density=LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density,scale)) {
                NoctuaTheme { Surface(Modifier.widthIn(max=width.dp).heightIn(max=520.dp).fillMaxSize()) {
                    when(screen) {
                        "report"->ReportScreen(data,c,3){}
                        "import"->ImportReviewScreen(vm,data,c){}
                        else->BackupDialog(vm){}
                    }
                } }
            }
        }
    }
    @After fun clean()=runBlocking {
        compose.runOnIdle{store.clear()}
        app.commands.awaitPendingWrites()
        val preview=app.repository.previewClassDeletion(c.id);app.repository.deleteClass(preview,preview.confirmation)
    }
    private fun shot(name:String) {
        val dir=File(app.filesDir,"ux12-screenshots");dir.mkdirs()
        val root=if(compose.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty())compose.onNode(isDialog())else compose.onRoot()
        File(dir,"$name.png").outputStream().use{root.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun reportAndImportActionsStayReachableAtSmallWidthsAndLargeFonts() {
        for((w,f) in listOf(320 to 1f,320 to 1.6f,360 to 1.3f)) {
            compose.runOnIdle{width=w;scale=f;screen="report"}
            compose.onNodeWithText("Geral ▾").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("report-sort").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("reports-list").performScrollToNode(hasTestTag("report-row-ux12-layout-student"))
            compose.onNodeWithTag("report-row-ux12-layout-student").assertIsDisplayed();shot("layout-report-$w-$f")
            compose.runOnIdle{screen="import"}
            compose.onNodeWithTag("import-back").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("import-select-all").performScrollTo().assertIsDisplayed();shot("layout-import-$w-$f")
            compose.onNodeWithTag("import-review-list").performScrollToNode(hasText("Confirmar cadastro de 1 aluno"))
            compose.onNodeWithText("Confirmar cadastro de 1 aluno").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
        }
    }
    @Test fun backupButtonsRemainReachableWithLargeFontsAndNoTechnicalIdentityBlock() {
        for((w,f) in listOf(320 to 1f,320 to 1.6f,360 to 1.3f)) {
            compose.runOnIdle{width=w;scale=f;screen="backup"}
            compose.onNodeWithText("Criar backup completo").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Selecionar backup para restaurar").performScrollTo().assertIsDisplayed()
            compose.onAllNodesWithText("SHA-256",substring=true).assertCountEquals(0)
            compose.onNodeWithText("Voltar").performScrollTo().assertIsDisplayed();shot("layout-backup-$w-$f")
        }
    }
}
