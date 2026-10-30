package br.com.diariodeclasse.sync

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import br.com.diariodeclasse.auth.AccountWorkspace
import br.com.diariodeclasse.data.SchoolClass
import kotlinx.coroutines.launch

/** Scope is always explicit: this status concerns classes, not an academic cloud backup. */
fun syncLabel(control:SyncControl?,states:List<SyncClassState>):String=when {
    control?.message=="RESTORE"->"Turmas: pausado após restauração"
    control?.enabled!=true->"Turmas: sincronização desativada"
    states.any{it.conflict!=null}->"Turmas: conflito preservado"
    control.message=="SETUP"->"Turmas: aguarda configuração"
    control.message.isNotBlank() && control.message!="PENDING"->"Turmas: falha de sincronização"
    states.any{it.localVersion>it.acknowledged} || !control.checked->"Turmas: alterações pendentes"
    else->"Turmas sincronizadas"
}
@Composable fun SyncPanel(workspace:AccountWorkspace,classes:List<SchoolClass>,close:()->Unit) {
    val dao=workspace.database.syncDao()
    val control by dao.watchControl().collectAsState(null)
    val states by dao.watchStates().collectAsState(emptyList())
    val scope=rememberCoroutineScope()
    val store=remember(workspace){ClassSyncStore(workspace.database,workspace.user.id)}
    var enrolling by remember{mutableStateOf<SchoolClass?>(null)}
    var error by remember{mutableStateOf("")}
    var busy by remember{mutableStateOf(false)}
    fun act(block:suspend()->Unit){busy=true;scope.launch{try{block();SyncJobs.soon(workspace.context)}catch(_:Exception){error="Não foi possível iniciar. Os dados locais foram preservados."}finally{busy=false}}}
    AlertDialog(onDismissRequest=close,title={Text("Sincronização de turmas • teste")},text={
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(syncLabel(control,states),Modifier.testTag("sync-status"))
            Text("Somente configurações da turma e bimestres. Alunos, notas, chamadas e diário continuam neste aparelho. Mantenha seus backups.")
            if(error.isNotEmpty())Text(error,color=MaterialTheme.colorScheme.error)
            if(control?.message=="RESTORE")Text("A restauração pausou a fila. Não retome antes de conferir as versões locais e remotas.")
            else if(control?.enabled!=true)OutlinedButton(onClick={act{store.enable()}},enabled=!busy){Text("Ativar teste nesta conta")}
            else {
                if(states.any{it.conflict!=null})Text("As versões em conflito estão preservadas. Nenhum dado foi sobrescrito. A resolução exige conferência antes de retomar.")
                OutlinedButton(onClick={act{}},enabled=!busy){Text("Tentar sincronizar")}
                TextButton(onClick={act{store.pause()}},enabled=!busy){Text("Pausar teste")}
                classes.forEach { school ->
                    val enrolled=states.any{it.classId==school.id}
                    OutlinedButton(onClick={enrolling=school},enabled=!enrolled&&!busy,modifier=Modifier.fillMaxWidth()) {
                        Text("${school.name} • ${school.subject} • ${school.year}"+if(enrolled)" • participante"else"")
                    }
                }
            }
        }
    },confirmButton={TextButton(close){Text("Concluído")}})
    enrolling?.let{school->AlertDialog(onDismissRequest={enrolling=null},title={Text("Sincronizar esta turma?")},text={
        Text("${school.name} • ${school.subject} • ${school.year}\n\nEnviar configurações e bimestres para sua conta. Nenhum aluno ou lançamento será enviado.")
    },dismissButton={TextButton({enrolling=null}){Text("Cancelar")}},confirmButton={TextButton({enrolling=null;act{store.enroll(school.id)}}){Text("Participar")}})}
}
