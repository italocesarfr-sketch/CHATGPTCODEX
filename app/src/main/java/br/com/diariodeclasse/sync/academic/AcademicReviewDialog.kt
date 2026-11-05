package br.com.diariodeclasse.sync.academic

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.*

private val entityNames=mapOf("classes" to "Turma","terms" to "Bimestre","students" to "Matrícula","activities" to "Atividade",
    "grades" to "Nota","lessons" to "Aula","marks" to "Frequência","recompositions" to "Recomposição","lesson_records" to "Diário",
    "relocations" to "Remanejamento","preserved_histories" to "Histórico preservado","weekly_timetables" to "Grade semanal",
    "export_rows" to "Disposição da planilha","class_archives" to "Arquivamento","import_drafts" to "Rascunho de importação")
private val fieldNames=mapOf("value" to "Nota","mark" to "Frequência","reason" to "Motivo","name" to "Nome","title" to "Título",
    "description" to "Descrição","notes" to "Observações","date" to "Data","number" to "Número da chamada","status" to "Situação",
    "joined" to "Ingresso","left" to "Saída","effectiveDate" to "Data efetiva","externalTarget" to "Destino externo",
    "corrections" to "Auditorias","slots" to "Horários","position" to "Posição","manualHidden" to "Ocultação manual","content" to "Conteúdo histórico",
    "stage" to "Etapa","subject" to "Disciplina","year" to "Ano letivo","shift" to "Turno","schedule" to "Horários informativos",
    "duration" to "Duração da aula (min)","scale" to "Escala final","low" to "Abaixo desta nota: vermelho","high" to "A partir desta nota: verde",
    "countJustified" to "FJ entra como ausência na frequência","startPresent" to "Iniciar chamadas com todos presentes",
    "start" to "Início do bimestre","end" to "Fim do bimestre","ordinal" to "Horário","state" to "Situação da aula","note" to "Observação da aula",
    "type" to "Tipo","term" to "Bimestre","maximum" to "Pontuação máxima","page" to "Página","reviewed" to "Revisado",
    "allowHomonym" to "Homônimo autorizado","effective" to "Início da vigência","revision" to "Revisão","createdAt" to "Criação da revisão")
private fun valueText(value:JsonElement?,field:String):String=when {
    value==null||value==JsonNull->when(field){"value"->"Sem lançamento";"reason"->"Sem motivo";else->"Em branco"}
    value is JsonPrimitive ->if(value.isString) {
        val text=value.content
        if(field in setOf("date","joined","left","effectiveDate","start","end","effective"))runCatching{java.time.LocalDate.parse(text).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))}.getOrDefault(text)else text
    }else if(value.booleanOrNull!=null)if(value.boolean)"Sim"else"Não"else value.content
    else->"Conteúdo estruturado • escolha aplicada ao conjunto"
}
private fun evidenceTitle(preview:AcademicPreview,e:AcademicEvidence):String {
    val values=(preview.local+preview.remote).associateBy{it.identity}
    val studentId=if(e.kind=="students")e.key["id"]else e.key["studentId"] ?: e.key["sourceStudentId"]
    val studentRow=studentId?.let{values[AcademicKey("students",buildJsonObject{put("id",it)})]?.payload}
    val student=studentRow?.get("name")?.jsonPrimitive?.content?.let{name->name+(studentRow["number"]?.jsonPrimitive?.content?.let{" • Nº $it"}.orEmpty())}
    val payload=e.local ?: e.remote
    val label=payload?.let{it["name"] ?: it["title"]}?.jsonPrimitive?.content
    val classId=studentRow?.get("classId") ?: payload?.get("classId") ?: e.key["classId"]
    val school=classId?.let{values[AcademicKey("classes",buildJsonObject{put("id",it)})]?.payload}
        ?: if(e.kind=="preserved_histories")payload?.get("content")?.jsonObject?.get("school")?.jsonObject else null
    val schoolLabel=school?.let{listOfNotNull(it["name"],it["subject"],it["year"]).joinToString(" • "){v->v.jsonPrimitive.content}}
    fun related(kind:String,field:String)=e.key[field]?.let{values[AcademicKey(kind,buildJsonObject{put("id",it)})]?.payload}
    val context=when(e.kind) {
        "grades"->related("activities","activityId")?.let{listOfNotNull(it["name"]?.jsonPrimitive?.content,it["date"]?.let{date->valueText(date,"date")}).joinToString(" • ")}
        "marks"->related("lessons","lessonId")?.let{valueText(it["date"],"date")+" • "+it.getValue("ordinal").jsonPrimitive.content+"º horário"}
        "recompositions","terms"->e.key[if(e.kind=="terms")"number"else"term"]?.jsonPrimitive?.content?.let{"${it}º bimestre"}
        "lesson_records"->(e.local ?: e.remote)?.get("date")?.let{valueText(it,"date")}
        else->null
    }
    return (listOf(entityNames.getValue(e.kind))+listOfNotNull(student ?: label,context,schoolLabel)).joinToString(" • ")
}

/** Review only; a single explicit confirmation freezes the decision for safe retry. */
@Composable fun AcademicReviewDialog(preview:AcademicPreview,busy:Boolean,error:String,onRefresh:()->Unit,
    onClose:()->Unit,onConfirm:(AcademicChoice)->Unit) {
    var choice by remember(preview.context,preview.head){mutableStateOf<AcademicChoice?>(null)}
    var index by remember(preview.context,preview.head){mutableIntStateOf(0)}
    AlertDialog(onDismissRequest={if(!busy)onClose()},title={Text(if(preview.restore)"Conferir backup e nuvem"else"Conferir versões")},text={
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("${preview.evidence.size} registros para conferência. Nenhuma versão será substituída sem sua decisão.")
            if(preview.restore)Text("Registros exclusivos do backup e registros novos da nuvem serão preservados. Ausência no backup não significa exclusão.")
            Text("A escolha vale para este conjunto. Matrículas, movimentações e históricos serão validados em conjunto.")
            preview.evidence.drop(index*5).take(5).forEach { e ->
                HorizontalDivider()
                Text(evidenceTitle(preview,e),style=MaterialTheme.typography.titleSmall)
                if(e.local==null)Text("Neste aparelho: sem registro")
                if(e.remote==null)Text("Sincronizado: sem registro")
                for(field in e.fields.filterNot{it in setOf("id","classId","personId","studentId","lessonId","activityId","sourceStudentId","targetStudentId")}) {
                    Text(fieldNames[field] ?: "Configuração",style=MaterialTheme.typography.labelLarge)
                    Text("Neste aparelho: ${valueText(e.local?.get(field),field)}")
                    Text("Sincronizado: ${valueText(e.remote?.get(field),field)}")
                }
            }
            if(preview.evidence.size>5)Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                TextButton(onClick={index--},enabled=index>0&&!busy){Text("Anteriores")}
                TextButton(onClick={index++},enabled=(index+1)*5<preview.evidence.size&&!busy){Text("Próximos")}
            }
            for(value in AcademicChoice.entries)Row(Modifier.fillMaxWidth().heightIn(min=48.dp).selectable(selected=choice==value,role=Role.RadioButton,enabled=!busy,onClick={choice=value}).testTag("academic-choice-${value.name}"),verticalAlignment=Alignment.CenterVertically) {
                RadioButton(selected=choice==value,onClick=null,enabled=!busy)
                Text(if(value==AcademicChoice.KEEP_REMOTE)"Usar valores sincronizados"else if(preview.restore)"Usar valores do backup"else"Usar valores deste aparelho",Modifier.weight(1f))
            }
            if(error.isNotBlank()){Text(error,color=MaterialTheme.colorScheme.error);TextButton(onClick=onRefresh,enabled=!busy){Text("Atualizar prévia")}}
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    },dismissButton={TextButton(onClick=onClose,enabled=!busy){Text("Cancelar")}},confirmButton={
        TextButton(onClick={choice?.let(onConfirm)},enabled=choice!=null&&!busy,modifier=Modifier.testTag("academic-confirm-decision")){Text("Confirmar decisão")}
    })
}
