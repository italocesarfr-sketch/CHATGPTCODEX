package br.com.diariodeclasse.sync

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import br.com.diariodeclasse.auth.AccountWorkspace
import br.com.diariodeclasse.data.SchoolClass
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import br.com.diariodeclasse.sync.academic.*
import br.com.diariodeclasse.DiaryApplication

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
    val academicDao=workspace.database.academicDao()
    val academic by academicDao.watchControl().collectAsState(null)
    val academicStates by academicDao.watchStates().collectAsState(emptyList())
    val pending by academicDao.watchPending().collectAsState(emptyList())
    val incoming by academicDao.watchIncoming().collectAsState(emptyList())
    val scope=rememberCoroutineScope()
    val store=remember(workspace){ClassSyncStore(workspace.database,workspace.user.id)}
    var enrolling by remember{mutableStateOf<SchoolClass?>(null)}
    var error by remember{mutableStateOf("")}
    var busy by remember{mutableStateOf(false)}
    var consent by remember{mutableStateOf(false)}
    var consentC by remember{mutableStateOf(false)}
    var consentD by remember{mutableStateOf(false)}
    var fictitiousD by remember{mutableStateOf(false)}
    var fictitiousC by remember{mutableStateOf(false)}
    var fictitious by remember{mutableStateOf(false)}
    fun act(block:suspend()->Unit){busy=true;scope.launch{try{block();SyncJobs.soon(workspace.context)}catch(_:Exception){error="Não foi possível iniciar. Os dados locais foram preservados."}finally{busy=false}}}
    AlertDialog(onDismissRequest=close,title={Text(if(academic==null)"Sincronização de turmas • teste"else if(academic!!.stage.endsWith("_D"))"Cadeia acadêmica • teste"else if(academic!!.stage.endsWith("_C"))"Avaliações e frequência • teste"else"Turmas e alunos • teste")},text={
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(academic!=null) {
                val academicStore=remember(workspace){AcademicStore(workspace.database,workspace.user.id)}
                AcademicPanelContent(academic!!,academicStates,pending,incoming,busy,
                    retry={workspace.academicCoordinator.request();SyncJobs.soon(workspace.context)},
                    pause={act{academicStore.pause()}},resume={act{
                        if(academic!!.stage in listOf("ACTIVE","ACTIVE_C","ACTIVE_D"))academicStore.resume()
                        workspace.academicCoordinator.request()
                    }})
                if(academic!!.stage=="ACTIVE_C" && academicTestEligible(workspace))OutlinedButton(onClick={consentD=true},
                    enabled=!busy&&pending.isEmpty()&&incoming.isEmpty(),modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("academic-enable-d")) {
                    Text("Incluir diário e históricos no teste")
                }
                if(academic!!.stage=="ACTIVE" && academicTestEligible(workspace))OutlinedButton(onClick={consentC=true},
                    enabled=!busy&&pending.isEmpty()&&incoming.isEmpty(),modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("academic-enable-c")) {
                    Text("Incluir notas e chamadas no teste")
                }
            } else {
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
            if(academicTestEligible(workspace))OutlinedButton(onClick={consent=true},enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("academic-enable")) {Text("Testar turmas e alunos")}
            }
            if(academic!=null && error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
        }
    },confirmButton={TextButton(close){Text("Concluído")}})
    if(consent)AlertDialog(onDismissRequest={consent=false},title={Text("Iniciar teste acadêmico?")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Turmas e alunos desta conta serão enviados. Versões antigas deixarão de sincronizar nesta conta. Use apenas dados fictícios e mantenha o backup.")
            Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(fictitious,{fictitious=it});Text("Esta conta contém somente dados fictícios.",Modifier.weight(1f))
            }
        }
    },dismissButton={TextButton({consent=false}){Text("Cancelar")}},confirmButton={TextButton({
        consent=false;act {
            check(academicTestEligible(workspace))
            val app=workspace.context.applicationContext as DiaryApplication
            workspace.syncMutex.withLock {AcademicCoordinator.engine(app,workspace).activate()}
            workspace.academicCoordinator.request()
        }
    },enabled=fictitious&&!busy){Text("Iniciar teste")}})
    if(consentC)AlertDialog(onDismissRequest={consentC=false},title={Text("Ampliar o teste?")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Avaliações, notas, aulas, frequências e recomposições desta conta serão enviados. Clientes anteriores deixarão de sincronizar. Diário e horários ainda não serão enviados.")
            Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(fictitiousC,{fictitiousC=it});Text("Esta conta contém somente dados fictícios.",Modifier.weight(1f))
            }
        }
    },dismissButton={TextButton({consentC=false}){Text("Cancelar")}},confirmButton={TextButton({
        consentC=false;act {
            check(academicTestEligible(workspace))
            val app=workspace.context.applicationContext as DiaryApplication
            workspace.syncMutex.withLock {AcademicCoordinator.engine(app,workspace).activateC()}
            workspace.academicCoordinator.request()
        }
    },enabled=fictitiousC&&!busy){Text("Ampliar teste")}})
    if(consentD)AlertDialog(onDismissRequest={consentD=false},title={Text("Incluir diário e históricos?")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Diários, remanejamentos, horários, históricos, disposição de planilhas e rascunhos desta conta serão enviados. Clientes anteriores deixarão de sincronizar. Use somente dados fictícios e mantenha o backup.")
            Row(Modifier.fillMaxWidth().heightIn(min=48.dp).toggleable(value=fictitiousD,role=Role.Checkbox,onValueChange={fictitiousD=it}),
                verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(fictitiousD,null);Text("Esta conta contém somente dados fictícios.",Modifier.weight(1f))
            }
        }
    },dismissButton={TextButton({consentD=false}){Text("Cancelar")}},confirmButton={TextButton({
        consentD=false;act {
            check(academicTestEligible(workspace))
            val app=workspace.context.applicationContext as DiaryApplication
            workspace.syncMutex.withLock {AcademicCoordinator.engine(app,workspace).activateD()}
            workspace.academicCoordinator.request()
        }
    },enabled=fictitiousD&&!busy){Text("Ampliar teste")}})
    enrolling?.let{school->AlertDialog(onDismissRequest={enrolling=null},title={Text("Sincronizar esta turma?")},text={
        Text("${school.name} • ${school.subject} • ${school.year}\n\nEnviar configurações e bimestres para sua conta. Nenhum aluno ou lançamento será enviado.")
    },dismissButton={TextButton({enrolling=null}){Text("Cancelar")}},confirmButton={TextButton({enrolling=null;act{store.enroll(school.id)}}){Text("Participar")}})}
}
