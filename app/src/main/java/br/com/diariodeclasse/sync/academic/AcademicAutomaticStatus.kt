package br.com.diariodeclasse.sync.academic

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import br.com.diariodeclasse.auth.AccountWorkspace
import kotlinx.coroutines.flow.combine

private data class AutomaticRead(val control:AcademicControl?,val states:List<AcademicState>,
    val pending:List<AcademicPending>,val incoming:List<AcademicIncoming>)

/** Same Room-backed status as the detailed panel, no optimistic success indicator. */
@Composable fun AcademicAutomaticStatus(workspace:AccountWorkspace,onDetails:()->Unit) {
    if(!academicSyncEligible(workspace))return
    val dao=remember(workspace){workspace.database.academicDao()}
    val flow=remember(dao){combine(dao.watchControl(),dao.watchStates(),dao.watchPending(),dao.watchIncoming(),::AutomaticRead)}
    val read by flow.collectAsState(initial=null)
    val startup by workspace.academicCoordinator.startupState.collectAsState()
    val label=read?.let { r->
        if(r.control==null)when(startup) {
            "SYNCING","PENDING"->"Verificando recuperação"
            "OFFLINE","NETWORK"->"Aguardando internet"
            "SETUP","INELIGIBLE"->if(br.com.diariodeclasse.BuildConfig.ACADEMIC_COMMERCIAL)"Sincronização indisponível para esta conta"else"Ativação inicial necessária"
            "REVIEW"->"Dados preservados • revisão necessária"
            else->"Falha temporária"
        } else academicLabel(r.control,r.states,r.pending,r.incoming)+
            if(r.control.stage !in setOf("ACTIVE_E","PREPARED_E","RESTORE"))" • escopo parcial"else""
    } ?: "Verificando recuperação"
    Text(label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,
        modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).clickable(onClick=onDetails)
            .padding(horizontal=16.dp,vertical=12.dp).testTag("automatic-sync-status")
            .semantics{liveRegion=LiveRegionMode.Polite;role=Role.Button})
}
