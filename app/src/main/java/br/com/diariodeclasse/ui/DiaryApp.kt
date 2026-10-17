package br.com.diariodeclasse.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.diariodeclasse.R
import br.com.diariodeclasse.backup.BackupDialog
import br.com.diariodeclasse.data.*
import kotlinx.coroutines.flow.receiveAsFlow
import java.time.LocalDate

private data class MainPage(val title: String, val label: String, val icon: Int)
private val pages = listOf(
    MainPage("Cadastrar Turma", "Cadastrar\nTurma", R.drawable.ic_classes),
    MainPage("Fazer Chamada", "Fazer\nChamada", R.drawable.ic_attendance),
    MainPage("Atividades e Notas", "Atividades\ne Notas", R.drawable.ic_grades),
    MainPage("Relatórios", "Relatórios", R.drawable.ic_reports))

@Composable fun DiaryApp(vm: DiaryViewModel) {
    val generation by vm.restoreGeneration.collectAsStateWithLifecycle()
    LaunchedEffect(generation) { if (generation > 0) vm.message(vm.restoreNotice) }
    key(generation) { DiaryContent(vm) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun DiaryContent(vm: DiaryViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val savedSelection by vm.selection.collectAsStateWithLifecycle()
    val selected = resolveSelection(savedSelection, data.classes)
    val c = data.classes.firstOrNull { it.id == selected.classId }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var detail by rememberSaveable { mutableStateOf("") }
    var returnDetail by rememberSaveable { mutableStateOf("") }
    var term by rememberSaveable { mutableIntStateOf((LocalDate.now().monthValue - 1) / 3 + 1) }
    val frequencyStates = rememberSaveableStateHolder()
    var activityId by rememberSaveable { mutableStateOf("") }
    var studentId by rememberSaveable { mutableStateOf("") }
    var classForm by remember { mutableStateOf(false) }
    var editingClass by remember { mutableStateOf<SchoolClass?>(null) }
    var termForm by remember { mutableStateOf(false) }
    var activityForm by remember { mutableStateOf(false) }
    var editingActivity by remember { mutableStateOf<Activity?>(null) }
    var showBackup by remember { mutableStateOf(false) }
    var invalidGrade by remember { mutableStateOf(false) }
    var pendingNavigation by remember { mutableStateOf<(() -> Unit)?>(null) }
    var navigating by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.receiveAsFlow().collect { snack.showSnackbar(it) } }
    LaunchedEffect(data.loaded, data.classes, savedSelection) {
        if (data.loaded && savedSelection == null) vm.initializeSelection()
    }
    // A fila já captura os IDs de origem. A navegação só é liberada depois das gravações.
    fun navigate(action: () -> Unit) {
        val safe = {
            navigating = true
            vm.act(done = { invalidGrade = false; action(); navigating = false }, clearError = false) {}
        }
        if (invalidGrade) pendingNavigation = safe else safe()
    }
    fun changeSelection(value: AppSelection) = navigate {
        vm.select(value) { detail = "" }
    }
    fun back() = navigate { detail = if (detail == "profile") returnDetail else ""; returnDetail = "" }
    BackHandler(detail.isNotEmpty() && !navigating) { back() }
    Scaffold(
        topBar = { TopAppBar(title = { Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(R.drawable.ic_owl), null, Modifier.size(24.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
            }
            Text("Seu espaço de gestão escolar", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }, navigationIcon = {
            if (detail.isNotEmpty()) TextButton(onClick = { back() }, enabled = !navigating) { Text("‹ Voltar") }
        }, actions = { TextButton(onClick = { showBackup = true }) { Text("Backup") } }) },
        bottomBar = { NavigationBar {
            pages.forEachIndexed { index, item -> NavigationBarItem(
                selected = page == index,
                onClick = { if (page != index || detail.isNotEmpty()) navigate { page = index; detail = "" } },
                enabled = !navigating,
                modifier = Modifier.testTag("nav-$index"),
                icon = { Icon(painterResource(item.icon), null, Modifier.size(23.dp)) },
                label = { Text(item.label, textAlign = TextAlign.Center, maxLines = 2, style = MaterialTheme.typography.labelSmall) }) }
        } }, snackbarHost = { SnackbarHost(snack) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            Box(Modifier.fillMaxWidth().height(4.dp)) { if (saving || navigating) LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (!data.loaded) { CircularProgressIndicator(Modifier.padding(32.dp)); return@Column }
            GlobalClassSelector(data.classes, selected, !navigating && !saving, ::changeSelection)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                key(c?.id, page) {
                    when {
                        detail == "grades" -> data.activities.firstOrNull { it.id == activityId && it.classId == c?.id }?.let { a ->
                            GradeScreen(vm, data, a, edit = { navigate { editingActivity = a; activityForm = true } },
                                deleted = { detail = "" }, dirty = { invalidGrade = it })
                        }
                        detail == "profile" && c != null -> data.students.firstOrNull { it.id == studentId && it.classId == c.id }?.let {
                            ProfileScreen(vm, data, c, it, term) { term = it }
                        }
                        detail == "roster" && c != null -> RosterScreen(vm, data, c) { studentId = it; returnDetail = "roster"; detail = "profile" }
                        page == 0 -> ClassManagementScreen(data, c, selected.year,
                            create = { editingClass = null; classForm = true },
                            edit = { editingClass = c; classForm = true }, roster = { detail = "roster" })
                        c == null -> EmptyClassPage(pages[page].title) { page = 0; editingClass = null; classForm = true }
                        page == 1 && c != null -> frequencyStates.SaveableStateProvider(c.id) {
                            CalendarScreen(vm, data, c, term, { term = it }, { termForm = true })
                        }
                        else -> Column {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(pages[page].title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                                TextButton(onClick = { termForm = true }) { Text("Bimestres") }
                            }
                            TermSelector(term) { next -> navigate { term = next; detail = "" } }
                            when (page) {
                                2 -> ActivitiesPage(vm, data, c, term, create = { editingActivity = null; activityForm = true },
                                    edit = { editingActivity = it; activityForm = true }) { activityId = it; detail = "grades" }
                                3 -> ReportScreen(data, c, term) { studentId = it; returnDetail = ""; detail = "profile" }
                            }
                        }
                    }
                }
            }
        }
    }
    pendingNavigation?.let { action -> Confirm("Sair sem salvar o valor inválido?",
        "Há uma nota inválida que não foi salva. As notas válidas já digitadas serão preservadas.",
        { pendingNavigation = null }, { pendingNavigation = null; action() }) }
    if (showBackup) BackupDialog(vm) { showBackup = false }
    if (classForm) ClassForm(vm, editingClass, selected.year, { classForm = false }) { id ->
        classForm = false
        // O formulário retorna o ano persistido, não a posição da turma no seletor.
        vm.act(done = { page = 0; detail = "roster" }) {
            val klass = requireNotNull(vm.repository.dao.schoolClass(id))
            (vm.getApplication<android.app.Application>() as br.com.diariodeclasse.DiaryApplication).selectionStore.write(AppSelection(klass.year, id))
        }
    }
    if (termForm && c != null) TermForm(vm, c, data.terms.filter { it.classId == c.id }) { termForm = false }
    if (activityForm && c != null) ActivityForm(vm, c, term, editingActivity, { activityForm = false }) {
        activityForm = false; activityId = it.id; term = it.term
        detail = if (editingActivity == null) "grades" else ""
    }
}

@Composable private fun GlobalClassSelector(classes: List<SchoolClass>, selected: AppSelection, enabled: Boolean, change: (AppSelection) -> Unit) {
    var yearsOpen by remember { mutableStateOf(false) }
    var classesOpen by remember { mutableStateOf(false) }
    val klass = classes.firstOrNull { it.id == selected.classId }
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box {
                OutlinedButton(onClick = { yearsOpen = true }, enabled = enabled, modifier = Modifier.testTag("year-selector")) { Text("${selected.year} ▾") }
                DropdownMenu(yearsOpen, { yearsOpen = false }) {
                    (classes.map { it.year } + selected.year + LocalDate.now().year).distinct().sortedDescending().forEach { year ->
                        DropdownMenuItem(text = { Text("Ano letivo $year") }, onClick = {
                            yearsOpen = false; change(AppSelection(year, classes.firstOrNull { it.year == year }?.id))
                        })
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                FilledTonalButton(onClick = { classesOpen = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("class-selector")) {
                    Column(Modifier.weight(1f)) {
                        Text(klass?.name ?: "Selecionar turma", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (klass != null) Text(klass.subject, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text("▾")
                }
                DropdownMenu(classesOpen, { classesOpen = false }) {
                    val options = classes.filter { it.year == selected.year }
                    if (options.isEmpty()) DropdownMenuItem(text = { Text("Nenhuma turma neste ano") }, enabled = false, onClick = {})
                    options.forEach { item -> DropdownMenuItem(text = { Column {
                        Text(item.name); Text("${item.subject} • ${item.shift} • ${item.year}", style = MaterialTheme.typography.bodySmall)
                    } }, onClick = { classesOpen = false; change(AppSelection(item.year, item.id)) }) }
                }
            }
        }
    }
}

@Composable private fun ClassManagementScreen(data: DiarySnapshot, c: SchoolClass?, year: Int, create: () -> Unit, edit: () -> Unit, roster: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Section("Cadastrar Turma", "Organize suas turmas e seus estudantes")
            Button(create, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("+ Nova Turma") }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(edit, enabled = c != null, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Editar Turma Existente") }
        }
        if (c == null) item { Info("Nenhuma turma selecionada em $year. Comece em Nova Turma ou escolha outro ano no seletor acima.") }
        else {
            item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("TURMA SELECIONADA", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(c.name, style = MaterialTheme.typography.headlineMedium)
                Text("${c.subject} • ${c.stage}\n${c.shift} • ano letivo ${c.year}")
                HorizontalDivider()
                Text("${counted(data.roster(c.id).count { it.status == "Ativo" }, "aluno ativo", "alunos ativos")} • ${counted(data.roster(c.id).size, "matrícula", "matrículas")}", style = MaterialTheme.typography.titleMedium)
                Button(roster, Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("Gerenciar alunos") }
                Text("Cadastro individual, colagem de lista, CSV e organização da chamada.", style = MaterialTheme.typography.bodySmall)
            } } }
            val drafts = data.drafts.count { it.classId == c.id }
            if (drafts > 0) item { OutlinedCard(onClick = roster, modifier = Modifier.fillMaxWidth()) { Text("${counted(drafts, "nome em rascunho aguarda", "nomes em rascunho aguardam")} conferência. Toque para continuar.", Modifier.padding(18.dp)) } }
            item { Text("Disponível de janeiro a dezembro de ${c.year}. A data de cadastro não limita o calendário.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable private fun EmptyClassPage(title: String, create: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Section(title, "Escolha uma turma para continuar")
        Info("Selecione o ano e a turma acima. Se ainda não houver turmas, faça seu primeiro cadastro.")
        Button(create, Modifier.fillMaxWidth()) { Text("Nova Turma") }
    }
}

@Composable private fun ActivitiesPage(vm: DiaryViewModel, data: DiarySnapshot, c: SchoolClass, term: Int, create: () -> Unit, edit: (Activity) -> Unit, grades: (String) -> Unit) {
    val activities = data.activities.filter { it.classId == c.id && it.term == term }
    var removing by remember { mutableStateOf<Activity?>(null) }
    LazyColumn(Modifier.testTag("activities-list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Button(create, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("+ Criar atividade") } }
        if (activities.isEmpty()) item { Info("Nenhuma atividade neste bimestre. Crie uma atividade para lançar notas.") }
        items(activities, key = { it.id }) { a -> Card(Modifier.fillMaxWidth().testTag("activity-${a.id}")) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(a.name, Modifier.clickable { grades(a.id) }, style = MaterialTheme.typography.titleMedium)
                Text("${a.type} • ${shortDate(a.date)} • ${number(a.maximum)} pontos")
                val pending = data.roster(c.id).filter { it.enrolled(a.date) }.count { s -> data.grades.none { it.activityId == a.id && it.studentId == s.id && it.value != null } }
                Text("${counted(pending, "nota pendente", "notas pendentes")}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { grades(a.id) }, modifier = Modifier.testTag("activity-grades-${a.id}"), contentPadding = PaddingValues(4.dp)) { Text("Lançar notas") }
                    TextButton(onClick = { edit(a) }, modifier = Modifier.testTag("activity-edit-${a.id}"), contentPadding = PaddingValues(4.dp)) { Text("Editar") }
                    TextButton(onClick = { removing = a }, modifier = Modifier.testTag("activity-delete-${a.id}"), contentPadding = PaddingValues(4.dp)) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
                }
            }
        } }
    }
    removing?.let { a -> DeleteActivityDialog(vm, a.id, { removing = null }) { removing = null } }
}

@Composable fun TermSelector(selected: Int, select: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..4).forEach { n -> FilterChip(modifier = Modifier.weight(1f), selected = selected == n,
            onClick = { if (selected != n) select(n) }, label = { Text("${n}º bim.", maxLines = 1, style = MaterialTheme.typography.labelSmall) }) }
    }
}
