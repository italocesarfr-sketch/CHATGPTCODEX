package br.com.diariodeclasse.sync.academic

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

fun academicLabel(c:AcademicControl?,states:List<AcademicState>,pending:List<AcademicPending>,incoming:List<AcademicIncoming>):String=when {
    c?.stage=="RESTORE"->"Pausado após restauração"
    pending.any{it.conflict!=null} || incoming.isNotEmpty()->"Conflito preservado"
    c?.message=="SYNCING"->"Sincronizando"
    c?.stage in listOf("PREPARED","PREPARED_C")->when(c?.message){"CONFLICT","GRAPH","UNSUPPORTED"->"Conflito preservado";"SETUP"->"Aguarda configuração do teste";"OFFLINE","NETWORK"->"Aguardando internet";else->"Transição pendente"}
    c?.enabled!=true->"Teste pausado"
    c.message=="OFFLINE"->"Aguardando internet"
    c.message=="SESSION"->"Acesso ao teste indisponível"
    c.message=="SETUP"->"Aguarda configuração do teste"
    c.message in setOf("CONFLICT","GRAPH","UNSUPPORTED")->"Conflito preservado"
    c.message in setOf("NETWORK","REMOTE","RETRY","SIZE")->"Falha temporária"
    pending.isNotEmpty() || states.any{it.localVersion>it.acknowledged} || !c.checked || c.message=="PENDING"->"Pendente de envio"
    else->"Sincronizado"
}
@Composable fun AcademicPanelContent(c:AcademicControl,states:List<AcademicState>,pending:List<AcademicPending>,incoming:List<AcademicIncoming>,busy:Boolean,
    retry:()->Unit,pause:()->Unit,resume:()->Unit) {
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            if(c.message=="SYNCING")CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp)
            Text(academicLabel(c,states,pending,incoming),Modifier.weight(1f).testTag("academic-status").semantics{liveRegion=LiveRegionMode.Polite})
        }
        Text(if(c.stage.endsWith("_C"))"Turmas, bimestres, alunos, avaliações e frequência. Diário, horários e remanejamentos ainda ficam neste aparelho. Mantenha o backup."
            else "Turmas, bimestres e alunos. Notas, chamadas e diário ainda ficam neste aparelho. Mantenha o backup.")
        if(incoming.isNotEmpty() || pending.any{it.conflict!=null})Text("As versões foram preservadas. A conferência é necessária antes de retomar.")
        if(c.stage=="RESTORE")Text("Confira os dados restaurados antes de reconciliar a fila.")
        else {
            OutlinedButton(onClick=retry,enabled=!busy&&c.message!="SYNCING",modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("academic-retry")){Text("Tentar novamente")}
            TextButton(onClick=if(c.enabled)pause else resume,enabled=!busy,modifier=Modifier.heightIn(min=48.dp)) {
                Text(if(c.enabled)"Pausar teste"else"Retomar teste")
            }
        }
    }
}
