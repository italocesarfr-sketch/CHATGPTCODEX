package br.com.diariodeclasse.ui

import br.com.diariodeclasse.ui.theme.NoctuaSpace
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.domain.RosterParser

/** Também recupera rascunhos de versões com fotografia, sem câmera nem OCR. */
@Composable fun ImportReviewScreen(vm: DiaryViewModel, data: DiarySnapshot, c: SchoolClass, guardNavigation: (((() -> Unit) -> Unit)?) -> Unit = {}, close: () -> Unit) {
    val rows = data.drafts.filter { it.classId == c.id }.sortedBy { it.position }
    val roster = data.roster(c.id)
    var joined by rememberSaveable(c.id) { mutableStateOf(shortDate(defaultEnrollment(c.year))) }
    var editing by remember { mutableStateOf<ImportDraft?>(null) }
    var removing by remember { mutableStateOf<ImportDraft?>(null) }
    var confirming by remember { mutableStateOf(false) }
    val saving by vm.saving.collectAsState()
    val error by vm.error.collectAsState()
    var exitAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var exitIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val exitRequest by rememberUpdatedState<(()->Unit)->Unit> { action ->
        if (!saving) {
            if (rows.isEmpty()) action() else { exitIds = rows.map { it.id }.toSet(); exitAction = action }
        }
    }
    val installGuard by rememberUpdatedState(guardNavigation)
    DisposableEffect(c.id) {
        installGuard { action -> exitRequest(action) }
        onDispose { installGuard(null) }
    }
    BackHandler { exitRequest(close) }
    fun issues(row: ImportDraft): String {
        val others = rows.filter { it.id != row.id }
        val duplicate = roster.any { RosterParser.possibleDuplicate(it.name, row.name) } || others.any { RosterParser.possibleDuplicate(it.name, row.name) }
        return listOfNotNull(
            "Nome incompleto".takeIf { row.name.trim().length < 2 },
            "Número inválido ou já utilizado".takeIf { row.number != null && (row.number <= 0 || roster.any { it.number == row.number } || others.any { it.number == row.number }) },
            "Possível duplicidade: corrija, exclua ou confirme homônimo".takeIf { duplicate && !row.allowHomonym },
            "Aguardando sua conferência".takeIf { !row.reviewed }
        ).joinToString(" • ")
    }
    val blocked = rows.isEmpty() || rows.any { issues(it).isNotEmpty() }
    LazyColumn(Modifier.fillMaxSize().testTag("import-review-list"), contentPadding = PaddingValues(NoctuaSpace.page), verticalArrangement = Arrangement.spacedBy(NoctuaSpace.medium)) {
        item {
            Section("Conferir importação", counted(rows.size, "aluno", "alunos"))
            Field("Ingresso dos novos alunos (dd/mm/aaaa)", joined, { joined = it })
            OutlinedButton(enabled = !saving, onClick = {
                editing = ImportDraft(classId = c.id, name = "", number = null, position = (rows.maxOfOrNull { it.position } ?: -1) + 1, page = 0)
            }, modifier = Modifier.fillMaxWidth()) { Text("+ Adicionar aluno") }
            TextButton(enabled = !saving, onClick = { exitRequest(close) }, modifier = Modifier.testTag("import-back")) { Text("Voltar") }
            val state = when { rows.isNotEmpty() && rows.all { it.reviewed } -> ToggleableState.On
                rows.any { it.reviewed } -> ToggleableState.Indeterminate
                else -> ToggleableState.Off }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                TriStateCheckbox(state=state, enabled=!saving && rows.isNotEmpty(),
                    modifier=Modifier.testTag("import-select-all"), onClick={
                        val ids=rows.map{it.id}.toSet();val reviewed=state!=ToggleableState.On
                        vm.act {vm.repository.reviewDrafts(c.id,ids,reviewed)}
                    })
                Text("Selecionar todos")
            }
        }
        items(rows, key = { it.id }) { row ->
            val issue = issues(row)
            OutlinedCard(Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = if (issue.isNotBlank()) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(NoctuaSpace.page), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${row.number?.toString() ?: "Sem nº"} • ${row.name.ifBlank { "Nome não preenchido" }}", style = MaterialTheme.typography.titleMedium)
                    if (issue.isNotBlank()) Text(issue, style = MaterialTheme.typography.bodySmall)
                    Row {
                        TextButton(onClick = { editing = row }) { Text("Conferir / corrigir") }
                        TextButton(onClick = { removing = row }) { Text("Excluir") }
                    }
                    Row {
                        Checkbox(row.reviewed, modifier=Modifier.testTag("import-reviewed-${row.id}"), enabled = !saving, onCheckedChange = { reviewed -> val updated = row.copy(reviewed = reviewed); vm.act { vm.repository.dao.save(updated) } })
                        Text("Nome e número conferidos", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        item {
            if (blocked) Text("Confira todas as linhas e resolva os avisos para cadastrar.", color = MaterialTheme.colorScheme.error)
            Button(enabled = !blocked && !saving, onClick = { confirming = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Confirmar cadastro de ${counted(rows.size, "aluno", "alunos")}") }
        }
    }
    exitAction?.let { action ->
        AlertDialog(onDismissRequest = { if (!saving) exitAction = null },
            title = { Text("Deseja manter o rascunho?") },
            text = { error?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
            confirmButton = { TextButton(enabled = !saving, onClick = {
                exitAction = null; action()
            }, modifier = Modifier.testTag("import-keep")) { Text("Sim") } },
            dismissButton = { TextButton(enabled = !saving, onClick = {
                val ids = exitIds
                vm.act(done = { exitAction = null; action() }) { vm.repository.discardDrafts(c.id, ids) }
            }, modifier = Modifier.testTag("import-discard")) { Text("Não") } })
    }
    editing?.let { row ->
        var name by rememberSaveable(row.id) { mutableStateOf(row.name) }
        var number by rememberSaveable(row.id) { mutableStateOf(row.number?.toString() ?: "") }
        var homonym by rememberSaveable(row.id) { mutableStateOf(row.allowHomonym) }
        Form("Conferir nome e número", { editing = null }) {
            Field("Nome completo", name, { name = it }); Field("Número (opcional)", number, { number = it })
            Row { Checkbox(homonym, { homonym = it }); Text("Confirmo que é outra pessoa (homônimo)", Modifier.padding(top = 12.dp)) }
            Button(enabled = !saving, onClick = {
                val formName = name; val formNumber = number; val formHomonym = homonym
                vm.act(done = { editing = null }) {
                    require(formName.trim().length >= 2) { "Informe o nome completo." }
                    val n = if (formNumber.isBlank()) null else formNumber.toInt().also { require(it > 0) { "Use número positivo ou deixe vazio." } }
                    vm.repository.dao.save(row.copy(name = formName.trim(), number = n, reviewed = true, allowHomonym = formHomonym))
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Salvar nome conferido") }
            SaveError(vm)
        }
    }
    removing?.let { row -> Confirm("Excluir este rascunho?", "Somente a linha em conferência será removida. Alunos já cadastrados não serão alterados.", { removing = null }) {
        vm.act(done = { removing = null }) { vm.repository.dao.deleteDraft(row.id) }
    } }
    if (confirming) Confirm("Cadastrar os alunos conferidos?", "Cadastro de ${counted(rows.size, "aluno", "alunos")} em ${c.name}. Nenhum aluno existente será substituído. ${error.orEmpty()}", { confirming = false }) {
        val ingress = joined; val classId = c.id
        vm.act(done = { confirming = false; close() }) { vm.repository.confirmImport(classId, iso(ingress)) }
    }
}
