package br.com.diariodeclasse.ui

import androidx.activity.compose.BackHandler
import br.com.diariodeclasse.backup.BackupDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.ocr.PhotoImportScreen
import kotlinx.coroutines.flow.receiveAsFlow
import java.time.LocalDate

@Composable fun DiaryApp(vm: DiaryViewModel) {
    val generation by vm.restoreGeneration.collectAsStateWithLifecycle()
    LaunchedEffect(generation) { if (generation > 0) vm.message("Backup restaurado. Os dados anteriores foram guardados em uma cópia de segurança.") }
    key(generation) { DiaryContent(vm) }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun DiaryContent(vm: DiaryViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    var route by rememberSaveable { mutableStateOf("home") }
    var classId by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf("Calendário") }
    var term by rememberSaveable { mutableIntStateOf((LocalDate.now().monthValue - 1) / 3 + 1) }
    var year by rememberSaveable { mutableIntStateOf(LocalDate.now().year) }
    var lessonId by rememberSaveable { mutableStateOf("") }
    var activityId by rememberSaveable { mutableStateOf("") }
    var studentId by rememberSaveable { mutableStateOf("") }
    var classForm by remember { mutableStateOf(false) }; var editingClass by remember { mutableStateOf<SchoolClass?>(null) }
    var termForm by remember { mutableStateOf(false) }; var activityForm by remember { mutableStateOf(false) }
    var editingActivity by remember { mutableStateOf<Activity?>(null) }
    var showBackup by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.receiveAsFlow().collect { snack.showSnackbar(it) } }
    val c = data.classes.firstOrNull { it.id == classId }
    fun back() { route = if (route == "class") "home" else "class" }
    BackHandler(route != "home") { back() }
    val openClass: (SchoolClass, String) -> Unit = { value, target ->
        classId = value.id; year = value.year; tab = target; route = "class"
    }
    Scaffold(topBar = { TopAppBar(title = { Column {
        Text(if (route == "home") "Diário de Classe" else c?.name ?: "Diário de Classe")
        Text(if (route == "home") "Seu dia em sala de aula" else "${c?.subject.orEmpty()} • ${c?.year ?: year}", style = MaterialTheme.typography.labelMedium)
    } }, navigationIcon = { if (route != "home") TextButton(onClick = { back() }) { Text("‹ Voltar") } }, actions = { TextButton(onClick = { showBackup = true }) { Text("Backup") } }) },
        snackbarHost = { SnackbarHost(snack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (!data.loaded) { CircularProgressIndicator(Modifier.padding(32.dp)); return@Column }
            when (route) {
                "home" -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Section("Painel do professor", shortDate(today()))
                        Choice("Ano letivo", (data.classes.map { it.year } + LocalDate.now().year).distinct().sortedDescending().map { it.toString() }, year.toString()) { year = it.toInt() }
                        TermSelector(term) { term = it }
                        Button(onClick = { editingClass = null; classForm = true }, modifier = Modifier.fillMaxWidth()) { Text("+ Criar turma") }
                    }
                    item { Section("Aulas de hoje") }
                    val todays = data.lessons.filter { it.date == today() && it.state != "CANCELADA" && data.classes.any { c -> c.id == it.classId && c.year == year } }
                    if (todays.isEmpty()) item { Info("Nenhuma aula prevista para hoje. Selecione uma turma para consultar outras datas.") }
                    items(todays, key = { it.id }) { lesson ->
                        val klass = data.classes.first { it.id == lesson.classId }
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                            Text("${klass.name} • ${klass.subject} • ${lesson.ordinal}ª aula", style = MaterialTheme.typography.titleMedium)
                            Button(onClick = { classId = klass.id; lessonId = lesson.id; route = "attendance" }) { Text("Fazer chamada") }
                        } }
                    }
                    item { Section("Suas turmas") }
                    if (data.classes.none { it.year == year }) item { Info("Crie sua primeira turma, configure os bimestres e cadastre os alunos.") }
                    items(data.classes.filter { it.year == year }, key = { it.id }) { klass ->
                        Card(Modifier.fillMaxWidth().clickable { openClass(klass, "Calendário") }) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(klass.name, style = MaterialTheme.typography.titleLarge)
                            Text("${klass.subject} • ${klass.shift} • ${data.roster(klass.id).count { it.status == "Ativo" }} alunos")
                            Text("${klass.schedule.split(',').sumOf { it.toInt() }} aulas semanais", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { openClass(klass, "Calendário") }) { Text("Calendário escolar / Fazer chamada") }
                            TextButton(onClick = { openClass(klass, "Atividades") }) { Text("Avaliações e notas") }
                            TextButton(onClick = { openClass(klass, "Boletim") }) { Text("Relatórios e boletim") }
                        } }
                    }
                }
                "class" -> if (c != null) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { editingClass = c; classForm = true }) { Text("Editar turma") }
                        TextButton(onClick = { termForm = true }) { Text("Bimestres") }
                    }
                    TermSelector(term) { term = it }
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Calendário", "Alunos", "Atividades", "Boletim").forEach { label ->
                            FilterChip(selected = tab == label, onClick = { tab = label }, label = { Text(label) })
                        }
                    }
                    when (tab) {
                        "Calendário" -> CalendarScreen(vm, data, c, term) { lessonId = it; route = "attendance" }
                        "Alunos" -> RosterScreen(vm, data, c, photo = { route = "photo" }, profile = { studentId = it; route = "profile" })
                        "Atividades" -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            item { Button(onClick = { editingActivity = null; activityForm = true }, modifier = Modifier.fillMaxWidth()) { Text("+ Criar atividade") } }
                            val activities = data.activities.filter { it.classId == c.id && it.term == term }
                            if (activities.isEmpty()) item { Info("Nenhuma atividade neste bimestre. Crie uma atividade para lançar notas.") }
                            items(activities, key = { it.id }) { activity ->
                                Card(Modifier.fillMaxWidth().clickable { activityId = activity.id; route = "grades" }) { Column(Modifier.padding(16.dp)) {
                                    Text(activity.name, style = MaterialTheme.typography.titleMedium)
                                    Text("${activity.type} • ${shortDate(activity.date)} • ${number(activity.maximum)} pontos")
                                    TextButton(onClick = { activityId = activity.id; route = "grades" }) { Text("Lançar notas") }
                                } }
                            }
                        }
                        "Boletim" -> ReportScreen(data, c, term) { studentId = it; route = "profile" }
                    }
                }
                "attendance" -> data.lessons.firstOrNull { it.id == lessonId }?.let { AttendanceScreen(vm, data, it) }
                "grades" -> data.activities.firstOrNull { it.id == activityId }?.let { a ->
                    GradeScreen(vm, data, a, edit = { editingActivity = a; activityForm = true }, deleted = { route = "class" })
                }
                "profile" -> data.students.firstOrNull { it.id == studentId }?.let { s ->
                    if (c != null) ProfileScreen(vm, data, c, s, term, { term = it })
                }
                "photo" -> if (c != null) PhotoImportScreen(vm, data, c) { route = "class"; tab = "Alunos" }
            }
        }
    }
    if (showBackup) BackupDialog(vm) { showBackup = false }
    if (classForm) ClassForm(vm, editingClass, { classForm = false }) { id, photo ->
        classForm = false; classId = id; route = if (photo) "photo" else "class"; tab = "Alunos"
    }
    if (termForm && c != null) TermForm(vm, c, data.terms.filter { it.classId == c.id }) { termForm = false }
    if (activityForm && c != null) ActivityForm(vm, c, term, editingActivity, { activityForm = false }) {
        activityForm = false; activityId = it; route = "grades"
    }
}

@Composable fun TermSelector(selected: Int, select: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (1..4).forEach { n -> FilterChip(selected = selected == n, onClick = { select(n) }, label = { Text("${n}º bim.") }) }
    }
}
