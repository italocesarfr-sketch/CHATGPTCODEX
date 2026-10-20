package br.com.diariodeclasse

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import br.com.diariodeclasse.backup.diarySnapshot
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.ui.*
import br.com.diariodeclasse.ui.theme.NoctuaTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

class FinalUxLayoutInstrumentedTest {
    @get:Rule val compose=createComposeRule()
    private lateinit var app:DiaryApplication
    private lateinit var vm:DiaryViewModel
    private val store=androidx.lifecycle.ViewModelStore()
    private val c=SchoolClass("ux14-layout",name="Turma Fictícia Visual",subject="História",year=2026)
    private lateinit var data:DiarySnapshot
    private var scale by mutableStateOf(1f)
    private var screen by mutableStateOf("entry")
    private var enabled by mutableStateOf(true)
    private var organized=0;private var exported=0
    @Before fun setup()=runBlocking {
        app=ApplicationProvider.getApplicationContext();vm=DiaryViewModel(app);store.put("ux14",vm)
        app.repository.saveClass(c,"2026-01-01")
        val s=Student("ux14-layout-student","ux14-person",c.id,"Estudante Fictício Visual",1,"2026-01-01")
        app.repository.saveStudent(s)
        val a=Activity("ux14-layout-activity",c.id,"Avaliação Fictícia",date="2026-08-28",term=3,maximum=10.0)
        app.repository.saveActivity(a);app.repository.saveGrade(a.id,s.id,0.0)
        data=app.backupManager.snapshot().diarySnapshot()
        compose.setContent {
            val density=LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density,scale)) {
                NoctuaTheme {Surface(Modifier.widthIn(max=320.dp).heightIn(max=560.dp).fillMaxSize().testTag("ux14-host")) {
                    when(screen) {
                        "entry"->DemoEntryScreen{}
                        "actions"->Box {ReportActions({organized++},{exported++},enabled)}
                        "export"->ExportScreen(data,c,3,attendance={},annual={}){}
                        else->ExportLayoutScreen(vm,data,c,ExportEditSession.start(data,c.id,"ORDER"),{},{},{})
                    }
                }}
            }
        }
    }
    @After fun clean()=runBlocking {
        compose.runOnIdle{store.clear()};app.commands.awaitPendingWrites()
        val p=app.repository.previewClassDeletion(c.id);app.repository.deleteClass(p,p.confirmation)
    }
    private fun tag(v:String)=compose.onNodeWithTag(v)
    private fun shot(name:String) {
        val dir=File(app.filesDir,"ux14-screenshots");dir.mkdirs()
        File(dir,"$name.png").outputStream().use{compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun entryKeepsEveryActionReachableAtNormalAndLargeFonts() {
        for(f in listOf(1f,1.3f,1.6f)) {
            compose.runOnIdle{scale=f;screen="entry"}
            for(id in listOf("entry-email","entry-password","entry-enter","entry-create","entry-forgot"))
                tag(id).performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            shot("entrada-320-fonte-$f")
            tag("entry-logo").performScrollTo().assertIsDisplayed()
        }
    }
    @Test fun reportActionsHaveEqualBoundsOutlineAndAccessibleUntruncatedLabels() {
        compose.runOnIdle{screen="actions"}
        for(f in listOf(1f,1.6f)) {
            compose.runOnIdle{scale=f}
            val first=tag("export-organize");val second=tag("open-export")
            first.assertIsDisplayed().assertHeightIsAtLeast(48.dp);second.assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            val a=first.fetchSemanticsNode().boundsInRoot;val b=second.fetchSemanticsNode().boundsInRoot
            assertEquals(a.width,b.width,1f);assertEquals(a.height,b.height,1f);assertEquals(a.top,b.top,1f)
            assertTrue(a.right<=b.left)
            shot("acoes-320-fonte-$f")
            for(text in listOf("Organizar","Exportar")) {
                val result=mutableListOf<TextLayoutResult>()
                compose.onNodeWithText(text,useUnmergedTree=true).performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(result)}
                val layout=result.single()
                val detail="$text fonte=$f botao=${a.width}x${a.height}, texto=${layout.size}, paragrafo=${layout.multiParagraph.width}, maximo=${layout.layoutInput.constraints}"
                // TextLayoutResult compara largura Float com Int arredondado, gerando
                // falso overflow por frações de pixel, mesmo com muito espaço livre.
                // Validar a geometria real com somente a tolerância do arredondamento.
                assertEquals(detail,1,layout.lineCount)
                assertFalse(detail,layout.isLineEllipsized(0))
                assertFalse(detail,layout.multiParagraph.didExceedMaxLines)
                assertEquals(detail,text.length,layout.getLineEnd(0,visibleEnd=true))
                assertFalse(detail,layout.didOverflowHeight)
                assertTrue(detail,layout.multiParagraph.width<=layout.size.width+0.5f)
                assertTrue(detail,layout.getLineLeft(0)>=-0.5f)
                assertTrue(detail,layout.getLineRight(0)<=layout.size.width+0.5f)
                assertTrue(detail,layout.size.width<=layout.layoutInput.constraints.maxWidth)
            }
            val ia=first.captureToImage().asAndroidBitmap();val ib=second.captureToImage().asAndroidBitmap()
            assertEquals(ia.getPixel(ia.width/2,2),ib.getPixel(ib.width/2,2))
            assertEquals(ia.getPixel(4,ia.height/2),ib.getPixel(4,ib.height/2))
        }
        tag("export-organize").performClick();tag("open-export").performClick()
        assertEquals(1,organized);assertEquals(1,exported)
        compose.runOnIdle{enabled=false}
        tag("export-organize").assertIsNotEnabled();tag("open-export").assertIsNotEnabled()
    }
    @Test fun exportPreviewStartsEarlyAndActionsRemainReachableWithoutMutatingAnyRows() {
        val before=runBlocking{app.backupManager.snapshot()}
        compose.runOnIdle{screen="export"}
        val host=tag("ux14-host").fetchSemanticsNode().boundsInRoot
        val preview=tag("export-preview-heading").fetchSemanticsNode().boundsInRoot
        assertTrue("Prévia deve começar antes da metade da área útil",preview.top<host.top+host.height*0.5f)
        tag("export-preview-0").assertIsDisplayed();shot("02-exportar-compacto")
        for(f in listOf(1f,1.6f)) {
            compose.runOnIdle{scale=f}
            for(id in listOf("export-file","export-copy","export-copy-grades"))tag(id).performScrollTo().assertIsDisplayed()
        }
        compose.runOnIdle{scale=1f;screen="organize"}
        for(id in listOf("export-conclude","export-cancel","export-conclude-and-export"))tag(id).performScrollTo().assertIsDisplayed()
        shot("03-organizar-compacto")
        assertEquals(before,runBlocking{app.backupManager.snapshot()})
    }
}
