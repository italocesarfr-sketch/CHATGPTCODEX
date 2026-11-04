package br.com.diariodeclasse.sync.academic

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

/** Room flows may expose inbox/outbox before their control-message emission. */
fun academicConflict(c:AcademicControl?,pending:List<AcademicPending>,incoming:List<AcademicIncoming>)=
    incoming.isNotEmpty() || pending.any{it.conflict!=null} || c?.message in setOf("CONFLICT","GRAPH","UNSUPPORTED")
fun academicRunning(c:AcademicControl?,pending:List<AcademicPending>,incoming:List<AcademicIncoming>)=
    c?.stage!="RESTORE" && c?.message=="SYNCING" && !academicConflict(c,pending,incoming)
fun academicRetryAllowed(c:AcademicControl?,pending:List<AcademicPending>,incoming:List<AcademicIncoming>)=
    c!=null && c.stage!="RESTORE" && !academicRunning(c,pending,incoming) && !academicConflict(c,pending,incoming)

fun academicLabel(c:AcademicControl?,states:List<AcademicState>,pending:List<AcademicPending>,incoming:List<AcademicIncoming>):String=when {
    c?.stage=="RESTORE"->"Pausado após restauração"
    academicConflict(c,pending,incoming)->"Conflito preservado"
    c?.message=="SYNCING"->"Sincronizando"
    c?.stage in listOf("PREPARED","PREPARED_C","PREPARED_D","PREPARED_E")->when(c?.message){"CONFLICT","GRAPH","UNSUPPORTED"->"Conflito preservado";"SETUP"->"Aguarda configuração do teste";"OFFLINE","NETWORK"->"Aguardando internet";else->"Transição pendente"}
    c?.enabled!=true->"Teste pausado"
    c.message=="OFFLINE"->"Aguardando internet"
    c.message=="SESSION"->"Acesso ao teste indisponível"
    c.message=="SETUP"->"Aguarda configuração do teste"
    c.message in setOf("CONFLICT","GRAPH","UNSUPPORTED")->"Conflito preservado"
    c.stage.endsWith("_E")&&c.message=="SIZE"->"Acervo acima do limite do teste"
    c.message in setOf("NETWORK","REMOTE","RETRY","SIZE")->"Falha temporária"
    pending.isNotEmpty() || states.any{it.localVersion>it.acknowledged} || !c.checked || c.message=="PENDING"->"Pendente de envio"
    else->"Sincronizado"
}
@Composable fun AcademicPanelContent(c:AcademicControl,states:List<AcademicState>,pending:List<AcademicPending>,incoming:List<AcademicIncoming>,busy:Boolean,
    retry:()->Unit,pause:()->Unit,resume:()->Unit) {
    val running=academicRunning(c,pending,incoming)
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            if(running)CircularProgressIndicator(Modifier.testTag("academic-progress").size(20.dp),strokeWidth=2.dp)
            Text(academicLabel(c,states,pending,incoming),Modifier.weight(1f).testTag("academic-status").semantics{liveRegion=LiveRegionMode.Polite})
        }
        Text(if(c.stage.endsWith("_D") || c.stage.endsWith("_E"))"Teste dos 15 tipos de registros acadêmicos, incluindo diário, horários e históricos. Mantenha o backup; conflitos e acervos maiores ainda exigem conferência."
            else if(c.stage.endsWith("_C"))"Turmas, bimestres, alunos, avaliações e frequência. Diário, horários e remanejamentos ainda ficam neste aparelho. Mantenha o backup."
            else "Turmas, bimestres e alunos. Notas, chamadas e diário ainda ficam neste aparelho. Mantenha o backup.")
        if(incoming.isNotEmpty() || pending.any{it.conflict!=null})Text("As versões foram preservadas. A conferência é necessária antes de retomar.")
        if(c.stage=="RESTORE")Text("Confira os dados restaurados antes de reconciliar a fila.")
        else {
            OutlinedButton(onClick=retry,enabled=!busy&&academicRetryAllowed(c,pending,incoming),modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("academic-retry")){Text("Tentar novamente")}
            TextButton(onClick=if(c.enabled)pause else resume,enabled=!busy,modifier=Modifier.heightIn(min=48.dp)) {
                Text(if(c.enabled)"Pausar teste"else"Retomar teste")
            }
        }
    }
}
