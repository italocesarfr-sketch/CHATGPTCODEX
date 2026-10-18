package br.com.diariodeclasse.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import br.com.diariodeclasse.data.*

@Composable fun ArchivedClassesScreen(data:DiarySnapshot,open:(String)->Unit) {
    val ids=data.classArchives.map{it.classId}.toSet()
    val classes=data.classes.filter{it.id in ids}.sortedWith(compareByDescending<SchoolClass>{it.year}.thenBy{it.name}.thenBy{it.id})
    LazyColumn(Modifier.testTag("archived-classes"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item{Section("Turmas arquivadas","Histórico preservado; consultar não reativa a turma")}
        if(classes.isEmpty())item{Info("Nenhuma turma arquivada.")}
        items(classes,key={it.id}){c->OutlinedButton(onClick={open(c.id)},modifier=Modifier.fillMaxWidth().testTag("archived-${c.id}")){Text("${c.name} • ${c.subject} • ${c.year}")}}
    }
}
@Composable fun ClassLifecycleScreen(vm:DiaryViewModel,data:DiarySnapshot,classId:String,reports:()->Unit,export:()->Unit,records:()->Unit = {},removed:()->Unit) {
    val c=data.classes.firstOrNull{it.id==classId}
    val saving by vm.saving.collectAsState()
    var archiveConfirmation by remember{mutableStateOf(false)}
    var preview by remember{mutableStateOf<ClassDeletionPreview?>(null)}
    var confirmation by rememberSaveable(classId){mutableStateOf("")}
    if(c==null){Info("A turma não está mais disponível. Volte para selecionar outra turma.");return}
    val archived=data.classArchives.any{it.classId==classId}
    LazyColumn(Modifier.testTag("class-lifecycle"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            Section("Gerenciar turma","${c.name} • ${c.subject} • ${c.year}")
            Text(if(archived)"Arquivada • registros escolares preservados"else"Ativa")
            Button(enabled=!saving,onClick={archiveConfirmation=true},modifier=Modifier.testTag("archive-class")){Text(if(archived)"Reativar turma"else"Arquivar turma")}
            if(archived) {
                OutlinedButton(onClick=reports,modifier=Modifier.testTag("archived-reports")){Text("Consultar relatórios")}
                OutlinedButton(onClick=records,modifier=Modifier.testTag("archived-records")){Text("Consultar registros de aula")}
                OutlinedButton(onClick=export,modifier=Modifier.testTag("archived-export")){Text("Exportar dados")}
            }
            HorizontalDivider()
            Text("Exclusão permanente",style=MaterialTheme.typography.titleMedium)
            Text("Crie um backup completo antes de excluir. A prévia confere vínculos e informa todos os registros afetados.",style=MaterialTheme.typography.bodySmall)
            OutlinedButton(enabled=!saving,onClick={val id=c.id;vm.act {val p=vm.repository.previewClassDeletion(id);kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main){preview=p;confirmation=""}}},modifier=Modifier.testTag("delete-class-preview")){Text("Conferir exclusão da turma")}
            SaveError(vm)
        }
    }
    if(archiveConfirmation)Confirm(if(archived)"Reativar turma?"else"Arquivar turma?",
        "${c.name} (${c.year}). ${if(archived)"A turma voltará aos seletores ativos."else"A turma sairá dos seletores ativos e continuará disponível em Turmas arquivadas."} Nenhum registro escolar será alterado.",
        {archiveConfirmation=false},{val id=c.id;vm.act(done={archiveConfirmation=false}){vm.repository.archiveClass(id,!archived)}})
    preview?.let{p->Form("Excluir turma permanentemente",{if(!saving)preview=null}) {
        Text("${p.school.name} • ${p.school.subject} • ${p.school.year}",style=MaterialTheme.typography.titleMedium)
        p.counts.forEach{(label,count)->Text("$label: $count")}
        p.blockedReason?.let{Info(it)} ?: run {
            Info("Irreversível. Crie um backup completo pelo botão Backup antes de continuar. Somente os dados exclusivos desta turma serão removidos.")
            Text("Digite exatamente: ${p.confirmation}")
            Field("Identificação para excluir",confirmation,{confirmation=it},modifier=Modifier.testTag("delete-class-confirmation"))
            Button(enabled=!saving&&confirmation==p.confirmation,onClick={vm.act(done={preview=null;removed()}){vm.repository.deleteClass(p,confirmation)}},modifier=Modifier.testTag("delete-class-confirm")){Text("Excluir permanentemente")}
        }
        OutlinedButton(enabled=!saving,onClick={preview=null}){Text("Cancelar exclusão")}
        SaveError(vm)
    }}
}
