package br.com.diariodeclasse.ui

import br.com.diariodeclasse.ui.theme.NoctuaSpace
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import br.com.diariodeclasse.data.*

@Composable fun ArchivedClassesScreen(data:DiarySnapshot,open:(String)->Unit) {
    val ids=data.classArchives.map{it.classId}.toSet()
    val classes=data.classes.filter{it.id in ids}.sortedWith(compareByDescending<SchoolClass>{it.year}.thenBy{it.name}.thenBy{it.id})
    LazyColumn(Modifier.testTag("archived-classes"),contentPadding=PaddingValues(NoctuaSpace.page),verticalArrangement=Arrangement.spacedBy(NoctuaSpace.medium)) {
        item{Section("Turmas arquivadas","Histórico preservado; consultar não reativa a turma")}
        if(classes.isEmpty())item{Info("Nenhuma turma arquivada.")}
        items(classes,key={it.id}){c->OutlinedButton(onClick={open(c.id)},modifier=Modifier.fillMaxWidth().testTag("archived-${c.id}")){Text("${c.name} • ${c.subject} • ${c.year}")}}
    }
}
/** Somente apresentação: plano, impressão digital e transação permanecem no Repository. */
@Composable fun ClassAdministrativeActions(vm:DiaryViewModel,c:SchoolClass,archived:Boolean,
    finished:(String)->Unit,reports:()->Unit,export:()->Unit,records:()->Unit) {
    val saving by vm.saving.collectAsState()
    val error by vm.error.collectAsState()
    var archiveConfirmation by remember(c.id){mutableStateOf(false)}
    var deleting by remember(c.id){mutableStateOf(false)}
    var preview by remember(c.id){mutableStateOf<ClassDeletionPreview?>(null)}
    HorizontalDivider()
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        OutlinedButton(enabled=!saving,onClick={vm.clearError();archiveConfirmation=true},
            modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag("archive-class")) {
            Text(if(archived)"Reativar turma"else"Arquivar")
        }
        OutlinedButton(enabled=!saving,onClick={
            vm.clearError();preview=null;deleting=true
            // A prévia é interna. Abrir/cancelar o diálogo não modifica registros.
            vm.act {
                try {
                    val p=vm.repository.previewClassDeletion(c.id)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main){preview=p}
                } catch(e:kotlinx.coroutines.CancellationException){throw e}
                catch(e:Exception){throw IllegalArgumentException("Não foi possível conferir a turma. Feche e tente novamente.",e)}
            }
        },colors=ButtonDefaults.outlinedButtonColors(contentColor=MaterialTheme.colorScheme.error),
            modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag("delete-class-preview")){Text("Excluir")}
    }
    if(archived) {
        Text("Arquivada • registros escolares preservados",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick=reports,enabled=!saving,modifier=Modifier.testTag("archived-reports")){Text("Consultar relatórios")}
        OutlinedButton(onClick=records,enabled=!saving,modifier=Modifier.testTag("archived-records")){Text("Consultar registros de aula")}
        OutlinedButton(onClick=export,enabled=!saving,modifier=Modifier.testTag("archived-export")){Text("Exportar dados")}
    }
    if(archiveConfirmation) AlertDialog(
        onDismissRequest={if(!saving)archiveConfirmation=false},
        title={Text(if(archived)"Reativar turma?"else"Arquivar turma?")},
        text={Column {
            Text(if(archived)"Reativar a turma ${c.name}?" else "Arquivar a turma ${c.name}? Você poderá reativá-la em Turmas arquivadas.")
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        }},
        dismissButton={TextButton(enabled=!saving,onClick={archiveConfirmation=false}){Text("Cancelar")}},
        confirmButton={TextButton(enabled=!saving,onClick={
            vm.act(done={archiveConfirmation=false;finished(if(archived)"Turma reativada."else"Turma arquivada.")}) {
                try {vm.repository.archiveClass(c.id,!archived)}
                catch(e:kotlinx.coroutines.CancellationException){throw e}
                catch(e:Exception){throw IllegalArgumentException("Não foi possível atualizar a turma. Tente novamente.",e)}
            }
        }){Text("Confirmar")}})
    if(deleting) AlertDialog(
        onDismissRequest={if(!saving){deleting=false;preview=null}},
        modifier=Modifier.testTag("delete-class-dialog"),
        title={Text("Excluir turma?")},
        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Deseja realmente excluir a turma ${c.name}?\n\nO procedimento é irreversível.")
            if(saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(preview?.blockedReason!=null) Text("Não foi possível excluir: os vínculos precisam de revisão.",color=MaterialTheme.colorScheme.error)
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        }},
        dismissButton={TextButton(enabled=!saving,onClick={deleting=false;preview=null},modifier=Modifier.testTag("delete-class-cancel")){Text("Não")}},
        confirmButton={TextButton(enabled=!saving&&preview?.blockedReason==null&&preview!=null,
            colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.error),
            modifier=Modifier.testTag("delete-class-confirm"),onClick={
                val plan=preview ?: return@TextButton
                // A autorização explícita é este clique. Mantém as validações originais,
                // inclusive contexto imutável e rejeição de prévia obsoleta na transação.
                vm.act(done={deleting=false;preview=null;finished("Turma excluída.")}) {
                    try {vm.repository.deleteClass(plan,plan.confirmation)}
                    catch(e:kotlinx.coroutines.CancellationException){throw e}
                    catch(e:Exception){throw IllegalArgumentException("Não foi possível excluir a turma. Feche e tente novamente.",e)}
                }
            }){Text("Sim, excluir")}})
}
