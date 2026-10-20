package br.com.diariodeclasse.ui

import br.com.diariodeclasse.ui.theme.NoctuaSpace
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import br.com.diariodeclasse.data.*
import java.time.LocalDate

/** Quatro páginas mantidas; os dois modos compartilham apenas contexto e calendário visual. */
@Composable fun ClassJournalArea(vm:DiaryViewModel,data:DiarySnapshot,c:SchoolClass,term:Int,
    changeTerm:(Int)->Unit,configureTerms:()->Unit,mode:String,changeMode:(String)->Unit,
    draft:LessonRecordDraft?,changeDraft:(LessonRecordDraft?)->Unit,dismissDraft:()->Unit,
    navigate:(()->Unit)->Unit) {
    var month by rememberSaveable(c.id){mutableIntStateOf(LocalDate.now().monthValue)}
    var day by rememberSaveable(c.id){mutableStateOf<String?>(null)}
    var allRecords by rememberSaveable(c.id){mutableStateOf(false)}
    val states=rememberSaveableStateHolder()
    fun back(){navigate{day=null;allRecords=false}}
    BackHandler(mode=="Diário"&&(day!=null||allRecords)){back()}
    Column(Modifier.fillMaxSize().testTag("class-journal-area")) {
        Text("Fazer Chamada / Diário de Classe",Modifier.padding(horizontal=16.dp,vertical=8.dp),style=MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal=NoctuaSpace.page).testTag("class-area-modes")) {
            listOf("Chamada","Diário").forEachIndexed{index,label->SegmentedButton(selected=mode==label,onClick={if(mode!=label)changeMode(label)},
                shape=SegmentedButtonDefaults.itemShape(index,2),modifier=Modifier.heightIn(min=48.dp).testTag("class-mode-$label")){Text(label)}}
        }
        Spacer(Modifier.height(8.dp))
        if(mode=="Chamada") states.SaveableStateProvider("attendance") {
            CalendarScreen(vm,data,c,term,changeTerm,configureTerms,month){month=it}
        } else if(day!=null||allRecords) {
            key(day,allRecords){LessonRecordsScreen(vm,data,c,draft,changeDraft,dismissDraft,day=day,backToCalendar=::back)}
        } else {
            val records=data.lessonRecords.filter{it.classId==c.id}.groupBy{it.date}
            SchoolCalendar(c.year,month,"",{month=it},{day=it},marker={date->
                val count=records[date].orEmpty().size
                CalendarMarker(if(count>0)"•"else"",if(count>0)counted(count,"registro de aula","registros de aula")else"sem registro de aula")
            },instruction="",
                legend="• dia com registro de aula",tag="journal-calendar") {
                OutlinedButton(onClick={allRecords=true},modifier=Modifier.fillMaxWidth().testTag("journal-all-records")){Text("Consultar todos os registros")}
            }
        }
    }
}
