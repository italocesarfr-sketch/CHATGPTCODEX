package br.com.diariodeclasse.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.backup.BackupCodec
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.format.TextStyle

@Serializable private data class TimetableDraft(val year:Int,val weekday:Int,val classId:String,
    val effective:String,val periods:List<Int>,val expected:List<WeeklyTimetable>)

/** Uma única rotina do professor. Apenas a confirmação grava uma revisão, nunca aulas. */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun TimetableScreen(vm:DiaryViewModel,data:DiarySnapshot,year:Int,openClass:(String)->Unit,
    guard: (((() -> Unit) -> Unit)?) -> Unit, preferredClassId:String?=null) {
    var weekday by rememberSaveable { mutableIntStateOf(LocalDate.now().dayOfWeek.value) }
    var at by rememberSaveable(year) { mutableStateOf(if(LocalDate.now().year==year) today() else "$year-01-01") }
    var dateText by rememberSaveable(year) { mutableStateOf(shortDate(at)) }
    var history by rememberSaveable { mutableStateOf(false) }
    var revisionDetails by rememberSaveable { mutableStateOf<String?>(null) }
    var draftJson by rememberSaveable { mutableStateOf<String?>(null) }
    val draft=remember(draftJson){draftJson?.let{BackupCodec.json.decodeFromString<TimetableDraft>(it)}}
    fun update(d:TimetableDraft?){draftJson=d?.let{BackupCodec.json.encodeToString(it)}}
    var discard by remember { mutableStateOf<(() -> Unit)?>(null) }
    var preview by remember { mutableStateOf<TimetablePreview?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val saving by vm.saving.collectAsState()
    DisposableEffect(draftJson) {
        guard(if(draft!=null) { action -> discard=action } else null)
        onDispose {guard(null)}
    }
    fun close(){if(draft!=null) discard={update(null);preview=null} }
    BackHandler(draft!=null){close()}
    val current=Timetables.current(data.timetables,at)
    val schools=data.classes.filter{it.year==year&&data.classArchives.none{a->a.classId==it.id}}
    fun edit(classId:String,day:Int=weekday){
        val periods=current?.slots.orEmpty().filter{it.classId==classId&&it.weekday==day}.map{it.ordinal}
        update(TimetableDraft(year,day,classId,at,periods,data.timetables.filter{it.year==year}.sortedBy{it.id}));error=null
    }
    LazyColumn(Modifier.fillMaxSize().testTag("weekly-timetable"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text("Meus horários • $year",style=MaterialTheme.typography.titleMedium)
                TextButton(onClick={history=!history},modifier=Modifier.testTag("timetable-history")){Text("Vigências")}
            }
            FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                (1..7).forEach{d->FilterChip(weekday==d,{weekday=d},label={Text(DayOfWeek.of(d).getDisplayName(TextStyle.SHORT,brLocale))},modifier=Modifier.heightIn(min=48.dp).testTag("weekday-$d").semantics{contentDescription=DayOfWeek.of(d).getDisplayName(TextStyle.FULL,brLocale)})}
            }
            Text(DayOfWeek.of(weekday).getDisplayName(TextStyle.FULL,brLocale).replaceFirstChar{it.uppercase()},style=MaterialTheme.typography.titleLarge)
            Text("Rotina em ${shortDate(at)}",style=MaterialTheme.typography.bodyMedium)
            if(current!=null) Text("Vigência desde ${shortDate(current.effective)}",style=MaterialTheme.typography.bodySmall)
            Button(onClick={(schools.firstOrNull{it.id==preferredClassId}?:schools.firstOrNull())?.let{edit(it.id)}},enabled=schools.isNotEmpty()&&!saving,modifier=Modifier.testTag("add-weekly-hours")){Text("+ Adicionar horários")}
            if(schools.isEmpty()) Text("Cadastre uma turma ativa para adicionar horários.")
        }
        if(history) item {
            Field("Consultar data (dd/mm/aaaa)",dateText,{dateText=it},Modifier.testTag("timetable-reference"))
            TextButton(onClick={runCatching{iso(dateText)}.onSuccess{if(LocalDate.parse(it).year==year){at=it;error=null}else error="Escolha uma data de $year."}.onFailure{error="Use dd/mm/aaaa."}}){Text("Consultar rotina")}
            data.timetables.filter{it.year==year}.groupBy{it.effective}.toSortedMap(reverseOrder()).forEach{(date,rows)->
                TextButton(onClick={at=date;dateText=shortDate(date)},modifier=Modifier.testTag("validity-$date")){Text("${shortDate(date)} • ${counted(rows.size,"revisão","revisões")}")}
                rows.sortedByDescending{it.revision}.forEach { r ->
                    TextButton(onClick={revisionDetails=if(revisionDetails==r.id)null else r.id}){Text("Revisão ${r.revision} • ${counted(r.slots.size,"período","períodos")}")}
                    if(revisionDetails==r.id) {
                        if(r.slots.isEmpty()) Text("Grade sem períodos.")
                        r.slots.sortedWith(compareBy({it.weekday},{it.ordinal})).forEach { slot -> Text("${DayOfWeek.of(slot.weekday).getDisplayName(TextStyle.SHORT,brLocale)} • ${slot.ordinal}º • ${slot.className} • ${slot.subject}",style=MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }
        val grouped=current?.slots.orEmpty().filter{it.weekday==weekday}.groupBy{it.classId}.values.sortedBy{it.minOf{p->p.ordinal}}
        if(grouped.isEmpty()) item {Text("Nenhum horário previsto para este dia.",modifier=Modifier.testTag("empty-timetable"))}
        items(grouped,key={it.first().classId}) { slots ->
            val slot=slots.first();val school=data.classes.firstOrNull{it.id==slot.classId}
            val archived=data.classArchives.any{it.classId==slot.classId}
            Column(Modifier.fillMaxWidth().testTag("weekly-class-${slot.classId}")) {
                Text("${school?.name?:slot.className} • ${school?.subject?:slot.subject}",style=MaterialTheme.typography.titleMedium)
                Text(slots.sortedBy{it.ordinal}.joinToString(" • "){"${it.ordinal}º horário"})
                if(archived||school==null) Text(if(archived)"Turma arquivada" else "Turma excluída • previsão histórica",style=MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    if(school!=null&&!archived) TextButton(onClick={openClass(slot.classId)},modifier=Modifier.testTag("open-weekly-${slot.classId}")){Text("Abrir chamada")}
                    TextButton(onClick={edit(slot.classId)},enabled=!saving){Text("Editar horários")}
                }
                HorizontalDivider()
            }
        }
        error?.let{item{Text(it,color=MaterialTheme.colorScheme.error)}}
    }
    draft?.let { d ->
        if(preview==null) Form("Editar horários",::close) {
            Choice("Dia da semana",(1..7).map{DayOfWeek.of(it).getDisplayName(TextStyle.FULL,brLocale)},DayOfWeek.of(d.weekday).getDisplayName(TextStyle.FULL,brLocale)){label->val wd=(1..7).first{DayOfWeek.of(it).getDisplayName(TextStyle.FULL,brLocale)==label};update(d.copy(weekday=wd,periods=runCatching{Timetables.current(d.expected,d.effective)?.slots.orEmpty().filter{it.classId==d.classId&&it.weekday==wd}.map{it.ordinal}}.getOrDefault(emptyList())))}
            val choices=(schools+data.classes.filter{it.id==d.classId}).distinctBy{it.id}
            fun label(c:SchoolClass)="${c.name} • ${c.subject} • ${c.year} • ${c.shift}"
            val school=data.classes.firstOrNull{it.id==d.classId}
            Choice("Turma",choices.map(::label),school?.let(::label)?:"Turma excluída"){v->val id=choices.first{label(it)==v}.id;update(d.copy(classId=id,periods=runCatching{Timetables.current(d.expected,d.effective)?.slots.orEmpty().filter{it.classId==id&&it.weekday==d.weekday}.map{it.ordinal}}.getOrDefault(emptyList())))}
            Field("Válido a partir de (dd/mm/aaaa)",shortDate(d.effective),{update(d.copy(effective=runCatching{iso(it)}.getOrDefault(it)))},Modifier.testTag("timetable-effective"))
            Text("Períodos",style=MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                (1..12).forEach{n->FilterChip(n in d.periods,{update(d.copy(periods=if(n in d.periods)d.periods-n else(d.periods+n).sorted()))},label={Text("${n}º")},modifier=Modifier.heightIn(min=48.dp).testTag("weekly-period-$n"))}
            }
            TextButton(onClick={update(d.copy(periods=emptyList()))},modifier=Modifier.testTag("remove-weekly-hours")){Text("Retirar estes horários da grade")}
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            SaveError(vm)
            Button(enabled=!saving,onClick={
                val result=runCatching {
                    require(data.timetables.filter{it.year==d.year}.sortedBy{it.id}==d.expected){"A grade mudou. Feche e reabra a edição."}
                    val before=Timetables.current(d.expected,d.effective)?.slots.orEmpty()
                    val keep=before.filterNot{it.classId==d.classId&&it.weekday==d.weekday}
                    val existing=before.filter{it.classId==d.classId&&it.weekday==d.weekday}.associateBy{it.ordinal}
                    val new=d.periods.map{n->existing[n]?:WeeklySlot(classId=d.classId,weekday=d.weekday,ordinal=n,className=requireNotNull(school){"Turma não encontrada."}.name,subject=school.subject,shift=school.shift)}
                    Timetables.preview(data,d.year,d.effective,keep+new)
                }
                result.onSuccess{preview=it;error=null}.onFailure{error=it.message?:"Confira os horários."}
            },modifier=Modifier.fillMaxWidth().testTag("review-weekly-hours")){Text("Revisar grade")}
        } else preview?.let{ p ->
            Form("Confirmar grade",{preview=null}) {
                Text("${shortDate(p.proposed.effective)} a ${shortDate(p.through)}",style=MaterialTheme.typography.titleMedium)
                Text("Somente a previsão será alterada. Chamadas e conteúdos permanecem intactos.")
                if(p.affected.isNotEmpty()) {
                    Text("Datas com registros existentes:",style=MaterialTheme.typography.titleSmall)
                    p.affected.forEach{row->val date=row.substringBefore(" •");Text("${shortDate(date)} • ${row.substringAfter(" • ")}")}
                }
                SaveError(vm)
                Button(enabled=!saving,onClick={vm.act(done={preview=null;update(null);at=p.proposed.effective;dateText=shortDate(at);vm.message("Grade salva.")}){vm.repository.confirmTimetable(p)}},modifier=Modifier.fillMaxWidth().testTag("save-weekly-hours")){Text("Confirmar grade")}
            }
        }
    }
    discard?.let{ action->Confirm("Descartar edição da grade?","A grade salva não será alterada.",{discard=null}){discard=null;update(null);preview=null;action()}}
}
