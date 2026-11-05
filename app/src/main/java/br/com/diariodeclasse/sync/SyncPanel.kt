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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.withLock
import br.com.diariodeclasse.sync.academic.*
import br.com.diariodeclasse.DiaryApplication

private data class LegacyPanelRead(val control:SyncControl?,val states:List<SyncClassState>)
private data class AcademicPanelRead(val control:AcademicControl?,val states:List<AcademicState>,
    val pending:List<AcademicPending>,val incoming:List<AcademicIncoming>,val decisions:List<AcademicDecision>)

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
    val dao=remember(workspace){workspace.database.syncDao()}
    val academicDao=remember(workspace){workspace.database.academicDao()}
    // Stable producers survive parent recompositions. Null snapshot means loading,
    // whereas a loaded snapshot with null control means no academic enrollment.
    val legacyFlow=remember(dao){combine(dao.watchControl(),dao.watchStates(),::LegacyPanelRead)}
    val academicFlow=remember(academicDao){combine(academicDao.watchControl(),academicDao.watchStates(),
        academicDao.watchPending(),academicDao.watchIncoming(),academicDao.watchDecisions(),::AcademicPanelRead)}
    // Publish visual state on Android's UI dispatcher regardless of the observer context.
    // Query execution remains on Room's executor.
    val legacyRead by legacyFlow.collectAsState(initial=null,context=Dispatchers.Main.immediate)
    val academicRead by academicFlow.collectAsState(initial=null,context=Dispatchers.Main.immediate)
    val control=legacyRead?.control
    val states=legacyRead?.states.orEmpty()
    val academic=academicRead?.control
    val academicStates=academicRead?.states.orEmpty()
    val pending=academicRead?.pending.orEmpty()
    val incoming=academicRead?.incoming.orEmpty()
    val decisionPending=academicRead?.decisions.orEmpty().any{it.phase in setOf("PENDING","CONFIRMED")}
    val ready=academicRead!=null && (academic!=null || legacyRead!=null)
    val scope=rememberCoroutineScope()
    val store=remember(workspace){ClassSyncStore(workspace.database,workspace.user.id)}
    var enrolling by remember{mutableStateOf<SchoolClass?>(null)}
    var preview by remember(workspace){mutableStateOf<AcademicPreview?>(null)}
    var reviewError by remember(workspace){mutableStateOf("")}
    var error by remember{mutableStateOf("")}
    var busy by remember{mutableStateOf(false)}
    var consent by remember{mutableStateOf(false)}
    var consentC by remember{mutableStateOf(false)}
    var consentD by remember{mutableStateOf(false)}
    var consentE by remember{mutableStateOf(false)}
    var fictitiousE by remember{mutableStateOf(false)}
    var fictitiousD by remember{mutableStateOf(false)}
    var fictitiousC by remember{mutableStateOf(false)}
    var fictitious by remember{mutableStateOf(false)}
    fun act(block:suspend()->Unit){busy=true;scope.launch{try{block();SyncJobs.soon(workspace.context)}catch(_:Exception){error="Não foi possível iniciar. Os dados locais foram preservados."}finally{busy=false}}}
    fun reconciliation():AcademicReconciliation {
        val app=workspace.context.applicationContext as DiaryApplication
        return AcademicReconciliation(AcademicStore(workspace.database,workspace.user.id),app.academicRemote(),{app.auth.syncToken(workspace.user.id)},{AcademicCoordinator.admitted(app,workspace)})
    }
    fun review(){busy=true;reviewError="";scope.launch {
        try{preview=workspace.syncMutex.withLock{reconciliation().preview()}}
        catch(e:Exception){reviewError=when((e as? SyncFailure)?.code){
            "SETUP"->"Esta conta ainda não está autorizada para a reconciliação E. Os dados e o backup foram preservados."
            "SESSION"->"Entre novamente para conferir as versões desta conta."
            "SIZE"->"O conjunto excede a capacidade desta conferência. Os dados foram preservados."
            else->"Não foi possível conferir as versões. Os dados continuam preservados. Atualize a prévia quando houver conexão."
        };error=reviewError}
        finally{busy=false}
    }}
    AlertDialog(onDismissRequest=close,title={Text(if(!ready)"Sincronização • teste"else if(academic==null)"Sincronização de turmas • teste"else if(academic!!.stage.endsWith("_D") || academic!!.stage.endsWith("_E"))"Cadeia acadêmica • teste"else if(academic!!.stage.endsWith("_C"))"Avaliações e frequência • teste"else"Turmas e alunos • teste")},text={
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(!ready)Text("Carregando estado da sincronização…",Modifier.testTag("sync-loading"))
            else if(academic!=null) {
                val academicStore=remember(workspace){AcademicStore(workspace.database,workspace.user.id)}
                AcademicPanelContent(academic!!,academicStates,pending,incoming,busy,
                    retry={workspace.academicCoordinator.request();SyncJobs.soon(workspace.context)},
                    pause={act{academicStore.pause()}},resume={act{
                        if(academic!!.stage in listOf("ACTIVE","ACTIVE_C","ACTIVE_D","ACTIVE_E"))academicStore.resume()
                        workspace.academicCoordinator.request()
                    }})
                if(decisionPending) {
                    Text("Decisão confirmada • aguardando conclusão. As versões continuam preservadas.")
                    OutlinedButton(onClick={workspace.academicCoordinator.request();SyncJobs.soon(workspace.context)},enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("academic-decision-retry")){Text("Tentar enviar decisão")}
                }else if(academic!!.stage in setOf("ACTIVE_E","RESTORE") && academicTestEligible(workspace) && (incoming.isNotEmpty()||pending.any{it.conflict!=null}||academic!!.stage=="RESTORE")) {
                    OutlinedButton(onClick={review()},enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("academic-review")){Text("Conferir versões")}
                }
                if(academic!!.stage=="ACTIVE_D" && academicTestEligible(workspace))OutlinedButton(onClick={consentE=true},
                    enabled=!busy&&pending.isEmpty()&&incoming.isEmpty(),modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("academic-enable-e")) {
                    Text("Testar recuperação de acervos maiores")
                }
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
    preview?.let { current->AcademicReviewDialog(current,busy,reviewError,{review()},{preview=null;reviewError=""}) { choice->
        busy=true;reviewError="";scope.launch {
            try {workspace.syncMutex.withLock{reconciliation().confirm(current,choice)};preview=null;workspace.academicCoordinator.request();SyncJobs.soon(workspace.context)}
            catch(_:Exception){reviewError="Não foi possível confirmar este conjunto. Os dados ou seus vínculos podem ter mudado. Atualize a prévia antes de decidir novamente."}
            finally{busy=false}
        }
    }}
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
    if(consentE)AlertDialog(onDismissRequest={consentE=false},title={Text("Ampliar a recuperação do teste?")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Os 15 tipos acadêmicos poderão ser enviados e recuperados por partes. Nenhum lote incompleto será aplicado. Clientes anteriores deixarão de sincronizar nesta conta. Mantenha o backup.")
            Row(Modifier.fillMaxWidth().heightIn(min=48.dp).toggleable(value=fictitiousE,role=Role.Checkbox,onValueChange={fictitiousE=it}),
                verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(fictitiousE,null);Text("Esta conta contém somente dados fictícios.",Modifier.weight(1f))
            }
        }
    },dismissButton={TextButton({consentE=false}){Text("Cancelar")}},confirmButton={TextButton({
        consentE=false;act {
            check(academicTestEligible(workspace))
            val app=workspace.context.applicationContext as DiaryApplication
            workspace.syncMutex.withLock {AcademicCoordinator.engine(app,workspace).activateE()}
            workspace.academicCoordinator.request()
        }
    },enabled=fictitiousE&&!busy){Text("Ampliar teste")}})
    enrolling?.let{school->AlertDialog(onDismissRequest={enrolling=null},title={Text("Sincronizar esta turma?")},text={
        Text("${school.name} • ${school.subject} • ${school.year}\n\nEnviar configurações e bimestres para sua conta. Nenhum aluno ou lançamento será enviado.")
    },dismissButton={TextButton({enrolling=null}){Text("Cancelar")}},confirmButton={TextButton({enrolling=null;act{store.enroll(school.id)}}){Text("Participar")}})}
}
