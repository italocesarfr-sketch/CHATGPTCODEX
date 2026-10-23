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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import br.com.diariodeclasse.data.*
import java.time.LocalDate

/** Quatro páginas mantidas; grade única e dois calendários compartilham contexto. */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun ClassJournalArea(vm:DiaryViewModel,data:DiarySnapshot,c:SchoolClass?,term:Int,
    changeTerm:(Int)->Unit,configureTerms:()->Unit,mode:String,changeMode:(String)->Unit,
    draft:LessonRecordDraft?,changeDraft:(LessonRecordDraft?)->Unit,dismissDraft:()->Unit,
    navigate:(()->Unit)->Unit,year:Int=c?.year?:LocalDate.now().year,openClass:(String)->Unit={},guard: (((() -> Unit) -> Unit)?) -> Unit = {}) {
    var month by rememberSaveable(c?.id){mutableIntStateOf(LocalDate.now().monthValue)}
    var day by rememberSaveable(c?.id){mutableStateOf<String?>(null)}
    var allRecords by rememberSaveable(c?.id){mutableStateOf(false)}
    val states=rememberSaveableStateHolder()
    fun back(){navigate{day=null;allRecords=false}}
    BackHandler(mode=="Diário"&&(day!=null||allRecords)){back()}
    Column(Modifier.fillMaxSize().testTag("class-journal-area")) {
        Text("Horários / Chamada / Diário",Modifier.padding(horizontal=16.dp,vertical=8.dp),style=MaterialTheme.typography.titleMedium)
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal=NoctuaSpace.page).testTag("class-area-modes")) {
            val labels=listOf("Horários","Chamada","Diário")
            if(LocalDensity.current.fontScale>1.2f && LocalDensity.current.fontScale<=1.6f && maxWidth>=270.dp) {
                TabRow(selectedTabIndex=labels.indexOf(mode).coerceAtLeast(0),containerColor=MaterialTheme.colorScheme.surface) {
                    labels.forEach { label -> Tab(selected=mode==label,onClick={if(mode!=label)changeMode(label)},
                        modifier=Modifier.heightIn(min=48.dp).testTag("class-mode-$label")) {
                        Text(label,style=MaterialTheme.typography.labelLarge,maxLines=1,softWrap=false,
                            modifier=Modifier.padding(vertical=8.dp).testTag("class-mode-label-$label"))
                    } }
                }
            } else if(LocalDensity.current.fontScale>1.6f || maxWidth<280.dp) {
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    labels.forEach { label -> FilterChip(selected=mode==label,onClick={if(mode!=label)changeMode(label)},
                        label={Text(label,style=MaterialTheme.typography.labelLarge,maxLines=1,softWrap=false,modifier=Modifier.testTag("class-mode-label-$label"))},
                        modifier=Modifier.heightIn(min=48.dp).testTag("class-mode-$label").semantics{role=Role.Tab}) }
                }
            } else SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                labels.forEachIndexed{index,label->SegmentedButton(selected=mode==label,onClick={if(mode!=label)changeMode(label)},
                    shape=SegmentedButtonDefaults.itemShape(index,3),icon={},modifier=Modifier.heightIn(min=48.dp).testTag("class-mode-$label")){
                    Text(label,style=MaterialTheme.typography.labelLarge,maxLines=1,softWrap=false,modifier=Modifier.testTag("class-mode-label-$label"))}}
            }
        }
        Spacer(Modifier.height(8.dp))
        if(mode=="Horários") {
            states.SaveableStateProvider("timetable") { TimetableScreen(vm,data,year,openClass,guard,preferredClassId=c?.id) }
        } else if(c==null) {
            Text("Selecione uma turma para ${if(mode=="Chamada") "fazer a chamada" else "consultar o diário"}.",Modifier.padding(16.dp))
        } else if(mode=="Chamada") states.SaveableStateProvider("attendance") {
            CalendarScreen(vm,data,c,term,changeTerm,configureTerms,month){month=it}
        } else if(day!=null||allRecords) {
            key(day,allRecords){LessonRecordsScreen(vm,data,c,draft,changeDraft,dismissDraft,day=day,backToCalendar=::back)}
        } else {
            val records=data.lessonRecords.filter{it.classId==c.id}.groupBy{it.date}
            SchoolCalendar(c.year,month,"",{month=it},{day=it},marker={date->
                val count=records[date].orEmpty().size
                CalendarMarker(if(count>0)"•"else"",if(count>0)counted(count,"registro de aula","registros de aula")else"sem registro de aula",Timetables.planned(data,c.id,date).size)
            },instruction="",
                legend="• registro de aula • pontos menores: aulas previstas",tag="journal-calendar") {
                OutlinedButton(onClick={allRecords=true},modifier=Modifier.fillMaxWidth().testTag("journal-all-records")){Text("Consultar todos os registros")}
            }
        }
    }
}
