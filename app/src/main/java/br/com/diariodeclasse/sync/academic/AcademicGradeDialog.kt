package br.com.diariodeclasse.sync.academic

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import br.com.diariodeclasse.DiaryApplication
import br.com.diariodeclasse.auth.AccountWorkspace
import br.com.diariodeclasse.backup.BackupCodec
import br.com.diariodeclasse.sync.SyncFailure
import br.com.diariodeclasse.sync.SyncJobs
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.withLock

@Composable fun NumericGradeConflictDialog(value:NumericGradeConflict,busy:Boolean,error:String,
    onRefresh:()->Unit,onClose:()->Unit,onConfirm:(AcademicChoice)->Unit) {
    AlertDialog(onDismissRequest={if(!busy)onClose()},modifier=Modifier.testTag("grade-conflict-dialog"),
        title={Text("Conflito de nota")},text={
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(value.student,style=MaterialTheme.typography.titleMedium)
                Text(value.classroom);Text(value.activity)
                Text("Neste aparelho: ${conflictGradeNumber(value.local)}")
                Text("Na nuvem: ${conflictGradeNumber(value.remote)}")
                Text("Conflito de nota identificado. Deseja manter a maior nota?")
                if(error.isNotBlank()) {
                    Text(error,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("grade-conflict-error"))
                    TextButton(onClick=onRefresh,enabled=!busy,modifier=Modifier.testTag("grade-conflict-refresh")){Text("Atualizar prévia")}
                }
                if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },confirmButton={
            Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(onClick={onConfirm(value.higherChoice)},enabled=!busy&&error.isBlank(),
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("grade-keep-${value.higherChoice.name}")) {
                    Text("Sim, manter ${conflictGradeNumber(value.higher)}")
                }
                OutlinedButton(onClick={onConfirm(value.otherChoice)},enabled=!busy&&error.isBlank(),
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("grade-keep-${value.otherChoice.name}")) {
                    Text("Não, manter ${conflictGradeNumber(value.other)}")
                }
            }
        })
}
private data class GradeConflictRead(val control:AcademicControl?,val pending:List<AcademicPending>,
    val incoming:List<AcademicIncoming>,val decisions:List<AcademicDecision>,val states:List<AcademicState>)

/** Presents a single valid numeric conflict. All other sets keep the existing review.
 * Dismissal changes only UI state; decisions and variants remain in this account's Room. */
@Composable fun AcademicGradeConflictHost(workspace:AccountWorkspace,enabled:Boolean) {
    val app=workspace.context.applicationContext as DiaryApplication
    if(!academicSyncEligible(workspace)||!app.automaticAcademicRecovery())return
    val dao=remember(workspace){workspace.database.academicDao()}
    val flow=remember(dao){combine(dao.watchControl(),dao.watchPending(),dao.watchIncoming(),dao.watchDecisions(),dao.watchStates(),::GradeConflictRead)}
    val read by flow.collectAsState(initial=null)
    var dismissed by rememberSaveable(workspace.user.id){mutableStateOf("")}
    var preview by remember(workspace){mutableStateOf<AcademicPreview?>(null)}
    var suggestion by remember(workspace){mutableStateOf<NumericGradeConflict?>(null)}
    var error by remember(workspace){mutableStateOf("")}
    var busy by remember(workspace){mutableStateOf(false)}
    var refresh by remember(workspace){mutableIntStateOf(0)}
    val scope=rememberCoroutineScope()
    val data=read
    val signature=remember(data?.incoming,data?.pending) {
        BackupCodec.hash((data?.incoming.orEmpty().map{it.sequence to it.reason}.toString()+
            data?.pending.orEmpty().filter{it.conflict!=null}.map{it.id}.toString()).toByteArray())
    }
    val blocked=data?.control?.stage=="ACTIVE_E" && data.control.enabled && data.states.any{it.kind=="grades"&&it.localVersion>it.acknowledged} && (data.incoming.isNotEmpty()||data.pending.any{it.conflict!=null})
    val pending=data?.decisions.orEmpty().any{it.phase in setOf("PENDING","CONFIRMED")}
    fun review()=AcademicReconciliation(AcademicStore(workspace.database,workspace.user.id),app.academicRemote(),
        {app.auth.syncToken(workspace.user.id)},{AcademicCoordinator.admitted(app,workspace)})
    LaunchedEffect(enabled,blocked,signature,pending,refresh,data?.control?.message) {
        if(!enabled||!blocked||pending){preview=null;suggestion=null;return@LaunchedEffect}
        if(signature==dismissed||busy||preview!=null)return@LaunchedEffect
        try {
            val result=withContext(Dispatchers.IO){workspace.syncMutex.withLock {
                val p=review().preview();p to AcademicNumericGrade.suggestion(p)
            }}
            if(result.second!=null){preview=result.first;suggestion=result.second;error=""}
        }catch(e:CancellationException){throw e}
        catch(_:Exception){/* Keep the conflict and existing status/review path; never log payloads. */}
    }
    val current=preview;val value=suggestion
    if(enabled&&current!=null&&value!=null)NumericGradeConflictDialog(value,busy,error,
        onRefresh={preview=null;suggestion=null;error="";refresh++},
        onClose={dismissed=signature;preview=null;suggestion=null;error=""}) { choice->
        busy=true;error=""
        scope.launch {
            try {
                val decision=withContext(Dispatchers.IO){workspace.syncMutex.withLock{review().confirmCurrent(current,choice)}}
                // Frozen decision is durable before any send; the same coordinator owns retries.
                preview=null;suggestion=null
                workspace.academicCoordinator.request();SyncJobs.soon(workspace.context)
                check(decision.owner==workspace.user.id)
            }catch(e:CancellationException){throw e}
            catch(e:Exception){error=if((e as? SyncFailure)?.code=="CONFLICT")
                "As versões mudaram. Atualize a prévia antes de decidir novamente."
                else "Não foi possível conferir estas versões. Atualize a prévia quando houver conexão."}
            finally{busy=false}
        }
    }
}
