package br.com.diariodeclasse.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.*
import br.com.diariodeclasse.ui.theme.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.diariodeclasse.R
import br.com.diariodeclasse.backup.BackupDialog
import br.com.diariodeclasse.data.*
import kotlinx.coroutines.flow.receiveAsFlow
import java.time.LocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import br.com.diariodeclasse.backup.BackupCodec

private data class MainPage(val title: String, val label: String, val icon: Int)
private val pages = listOf(
    MainPage("Cadastrar Turma", "Cadastrar\nTurma", R.drawable.ic_classes),
    MainPage("Fazer Chamada / Diário de Classe", "Chamada e Diário", R.drawable.ic_calendar),
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
    val activeClasses = data.classes.filter { c -> data.classArchives.none { it.classId == c.id } }
    val selected = resolveSelection(savedSelection, activeClasses)
    val c = data.classes.firstOrNull { it.id == selected.classId }
    var classAreaMode by rememberSaveable { mutableStateOf("Chamada") }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var detail by rememberSaveable { mutableStateOf("") }
    var managedClassId by rememberSaveable { mutableStateOf("") }
    var exportBusy by remember { mutableStateOf(false) }
    var returnDetail by rememberSaveable { mutableStateOf("") }
    var term by rememberSaveable { mutableIntStateOf((LocalDate.now().monthValue - 1) / 3 + 1) }
    val frequencyStates = rememberSaveableStateHolder()
    val reportStates = rememberSaveableStateHolder()
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
    var exportDraftJson by rememberSaveable { mutableStateOf<String?>(null) }
    val exportDraft = remember(exportDraftJson) { exportDraftJson?.let(ExportEditSession::decode) }
    fun changeExport(value: ExportEditSession?) { exportDraftJson = value?.encode() }
    var pendingExportNavigation by remember { mutableStateOf<(() -> Unit)?>(null) }
    var recordDraftJson by rememberSaveable { mutableStateOf<String?>(null) }
    val recordDraft = remember(recordDraftJson) { recordDraftJson?.let { BackupCodec.json.decodeFromString<LessonRecordDraft>(it) } }
    fun changeRecord(value: LessonRecordDraft?) { recordDraftJson = value?.let { BackupCodec.json.encodeToString(it) } }
    var pendingRecordNavigation by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun dismissRecord() { if (recordDraft?.dirty == true) pendingRecordNavigation = { changeRecord(null) } else changeRecord(null) }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.receiveAsFlow().collect { snack.showSnackbar(it) } }
    LaunchedEffect(data.loaded, data.classes, savedSelection) {
        if (data.loaded && savedSelection == null) vm.initializeSelection()
    }
    var importGuard by remember { mutableStateOf<((() -> Unit) -> Unit)?>(null) }
    // A fila já captura os IDs de origem. A navegação só é liberada depois das gravações.
    fun navigateUnchecked(action: () -> Unit) {
        if(exportBusy || (exportDraft != null || recordDraft != null) && saving) return
        val safe = {
            navigating = true
            vm.act(done = { invalidGrade = false; changeExport(null); changeRecord(null); action(); navigating = false }, clearError = false) {}
        }
        if(recordDraft?.dirty == true) pendingRecordNavigation = safe
        else if(exportDraft?.dirty == true) pendingExportNavigation = safe
        else if (invalidGrade) pendingNavigation = safe else safe()
    }
    fun navigate(action: () -> Unit) {
        val guard = importGuard
        if (guard != null) guard { navigateUnchecked(action) } else navigateUnchecked(action)
    }
    fun concludeExport(openFile: Boolean = false) {
        val draft = exportDraft ?: return
        vm.act {
            vm.repository.saveExportLayout(draft.classId,draft.expected,draft.rows)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                changeExport(null)
                if(detail == "export-layout") { page=3;detail=if(openFile) "export" else "" }
            }
        }
    }
    fun changeSelection(value: AppSelection) = navigate {
        vm.select(value) { detail = "" }
    }
    fun openReportStudent(school:SchoolClass,student:Student) = navigate {
        if(data.classArchives.any{it.classId==school.id}) {
            managedClassId=school.id;studentId=student.id;detail="archived-student-report"
        } else vm.select(AppSelection(school.year,school.id)) {studentId=student.id;detail="student-report"}
    }
    fun back() = navigate { detail = when(detail) {
        "attendance-export", "annual-export" -> "export"
        "profile" -> returnDetail
        "archived-student-report" -> "archived-reports"
        "archived-annual-export" -> "archived-export"
        "archived-reports", "archived-export", "archived-records", "archived-attendance-export" -> {
            editingClass=data.classes.firstOrNull{it.id==managedClassId};classForm=editingClass!=null;"archives"
        }
        else -> ""
    }; returnDetail = "" }
    BackHandler(detail.isNotEmpty() && !navigating) { back() }
    Scaffold(
        topBar = { TopAppBar(title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Image(painterResource(R.drawable.noctua_logo), "Logo do Noctua: coruja em forma de N",
                    Modifier.size(32.dp).testTag("noctua-logo"), contentScale = ContentScale.Fit)
                NoctuaWordmark()
            }
        }, navigationIcon = {
            if (detail.isNotEmpty()) TextButton(onClick = { back() }, enabled = !navigating && !exportBusy,contentPadding=PaddingValues(8.dp)) { Text("‹ Voltar") }
        }, actions = { TextButton(enabled = !exportBusy,contentPadding=PaddingValues(8.dp), onClick = {
            if(importGuard != null || exportDraft != null || recordDraft != null) navigate { showBackup = true } else showBackup = true
        }) { Text("Backup") } }) },
        bottomBar = { NavigationBar {
            pages.forEachIndexed { index, item ->
                val active=page==index
                Column(Modifier.weight(1f).heightIn(min=80.dp).testTag("nav-$index")
                    .selectable(selected=active,enabled=!navigating&&!exportBusy,role=Role.Tab,
                        onClick={if(page!=index||detail.isNotEmpty())navigate{page=index;detail=""}})
                    .semantics{contentDescription=if(index==1) "Chamada e Diário" else item.title}.padding(vertical=8.dp),
                    horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Surface(shape=MaterialTheme.shapes.large,color=if(active)MaterialTheme.colorScheme.secondaryContainer else Color.Transparent) {
                        Icon(painterResource(item.icon),if(index==1) "Calendário" else null,Modifier.padding(horizontal=14.dp,vertical=4.dp).size(23.dp).testTag("nav-icon-$index"),
                            tint=if(active)MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(item.label,Modifier.fillMaxWidth(),textAlign=TextAlign.Center,maxLines=3,minLines=if(LocalDensity.current.fontScale>1.3f)3 else 2,
                        style=MaterialTheme.typography.labelSmall,color=if(active)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } }, snackbarHost = { SnackbarHost(snack) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            Box(Modifier.fillMaxWidth().height(4.dp)) { if (saving || navigating) LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (!data.loaded) { CircularProgressIndicator(Modifier.padding(32.dp)); return@Column }
            if(detail !in setOf("archives","archived-reports","archived-export","archived-student-report","archived-records","archived-attendance-export","archived-annual-export")) GlobalClassSelector(activeClasses, selected, !navigating && !saving && !exportBusy, ::changeSelection)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                key(c?.id, page) {
                    when {
                        detail == "archives" -> ArchivedClassesScreen(data) { id ->
                            managedClassId=id;editingClass=data.classes.firstOrNull{it.id==id};classForm=editingClass!=null
                        }
                        detail == "archived-reports" -> data.classes.firstOrNull{it.id==managedClassId}?.let { archived ->
                            Column { Text("Turma arquivada • ${archived.name} • ${archived.year}",Modifier.padding(NoctuaSpace.page));TermSelector(term){term=it}
                                ReportScreen(data,archived,term,globalSearch=false) {studentId=it;detail="archived-student-report"} }
                        }
                        detail == "archived-student-report" -> data.classes.firstOrNull{it.id==managedClassId}?.let { archived -> data.students.firstOrNull{it.id==studentId&&it.classId==archived.id}?.let {IndividualReportScreen(data,archived,it,term){term=it}} }
                        detail == "archived-export" -> data.classes.firstOrNull{it.id==managedClassId}?.let{ExportScreen(data,it,term,vm::message,ready=!saving,attendance={detail="archived-attendance-export"},annual={detail="archived-annual-export"}){exportBusy=it}}
                        detail == "archived-attendance-export" -> data.classes.firstOrNull{it.id==managedClassId}?.let{AttendanceExportScreen(data,it,term,vm::message,!saving){exportBusy=it}}
                        detail == "archived-annual-export" -> data.classes.firstOrNull{it.id==managedClassId}?.let{AttendanceExportScreen(data,it,term,vm::message,!saving,annual=true){exportBusy=it}}
                        detail == "records" && c != null -> LessonRecordsScreen(vm,data,c,recordDraft,::changeRecord,::dismissRecord)
                        detail == "archived-records" -> data.classes.firstOrNull{it.id==managedClassId}?.let { LessonRecordsScreen(vm,data,it,recordDraft,::changeRecord,::dismissRecord,readOnly=true) }
                        detail == "attendance-export" && c != null -> AttendanceExportScreen(data,c,term,vm::message,!saving){exportBusy=it}
                        detail == "annual-export" && c != null -> AttendanceExportScreen(data,c,term,vm::message,!saving,annual=true){exportBusy=it}
                        detail == "export" && c != null -> ExportScreen(data,c,term,vm::message,ready=!saving,attendance={detail="attendance-export"},annual={detail="annual-export"}){exportBusy=it}
                        detail == "export-layout" && c != null && exportDraft?.classId == c.id -> ExportLayoutScreen(vm,data,c,exportDraft,::changeExport,{concludeExport()},{changeExport(null);detail=""},export={concludeExport(true)})
                        detail == "recomposition" && c != null -> RecompositionScreen(vm, data, c, term) { next -> navigate { term = next } }
                        detail == "grades" -> data.activities.firstOrNull { it.id == activityId && it.classId == c?.id }?.let { a ->
                            GradeScreen(vm, data, a, edit = { navigate { editingActivity = a; activityForm = true } },
                                deleted = { detail = "" }, dirty = { invalidGrade = it })
                        }
                        detail == "profile" && c != null -> data.students.firstOrNull { it.id == studentId && it.classId == c.id }?.let {
                            ProfileScreen(vm, data, c, it, term) { term = it }
                        }
                        detail == "student-report" && c != null -> data.students.firstOrNull { it.id == studentId && it.classId == c.id }?.let {
                            IndividualReportScreen(data, c, it, term) { term = it }
                        }
                        detail == "roster" && c != null -> RosterScreen(vm, data, c, importGuard = { importGuard = it }) { studentId = it; returnDetail = "roster"; detail = "profile" }
                        page == 0 -> ClassManagementScreen(data, c, selected.year,
                            create = { editingClass = null; classForm = true },
                            edit = { editingClass = c; classForm = true }, roster = { detail = "roster" }, archives={detail="archives"})
                        page == 3 && c == null -> Column {
                            Text("Relatórios",Modifier.padding(NoctuaSpace.page),style=MaterialTheme.typography.titleLarge)
                            TermSelector(term){term=it}
                            ReportScreen(data,null,term,globalProfile=::openReportStudent) {}
                        }
                        c == null -> EmptyClassPage(pages[page].title) { page = 0; editingClass = null; classForm = true }
                        page == 1 && c != null -> frequencyStates.SaveableStateProvider(c.id) {
                            ClassJournalArea(vm,data,c,term,{term=it},{termForm=true},classAreaMode,{next->navigate{classAreaMode=next}},recordDraft,::changeRecord,::dismissRecord,::navigate)
                        }
                        else -> Column {
                            Row(Modifier.fillMaxWidth().padding(horizontal = NoctuaSpace.page), verticalAlignment = Alignment.CenterVertically) {
                                Text(pages[page].title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                                TextButton(onClick = { termForm = true }) { Text("Bimestres") }
                            }
                            TermSelector(term) { next -> navigate { term = next; detail = "" } }
                            when (page) {
                                2 -> ActivitiesPage(vm, data, c, term, create = { editingActivity = null; activityForm = true }, recomposition = { detail = "recomposition" },
                                    edit = { editingActivity = it; activityForm = true }) { activityId = it; detail = "grades" }
                                3 -> Column {
                                    ReportActions(organize={navigate{changeExport(ExportEditSession.start(data,c.id,"ORDER"));detail="export-layout"}},export={detail="export"})
                                    reportStates.SaveableStateProvider(c.id) {
                                    ReportScreen(data, c, term,globalProfile=::openReportStudent) { studentId = it; returnDetail = ""; detail = "student-report" }
                                    }
                                }
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
    pendingExportNavigation?.let { action -> Confirm("Descartar alterações de exportação?",
        "Há alterações não confirmadas na disposição de exportação. Cancelar mantém a edição. Confirmar descarta somente esta sessão e continua a navegação; os registros escolares não mudam.",
        {pendingExportNavigation=null}, {pendingExportNavigation=null;action()}) }
    pendingRecordNavigation?.let { action -> Confirm("Descartar texto não salvo?",
        "As alterações deste registro ainda não foram salvas. Cancelar mantém o texto; confirmar descarta somente esta edição.",
        {pendingRecordNavigation=null}, {pendingRecordNavigation=null;action()}) }
    if (showBackup) BackupDialog(vm) { showBackup = false }
    if (classForm) ClassForm(vm, editingClass, selected.year, { classForm = false },
        administrativeFinished = { notice ->
            classForm=false;page=0;detail=""
            // Resolver pelo banco já confirmado; o Flow visual pode ainda estar emitindo.
            vm.act(done={vm.message(notice)}) {
                val dao=vm.repository.db.backupDao()
                val archivedIds=dao.classArchives().map{it.classId}.toSet()
                val active=dao.classes().filter{it.id !in archivedIds}
                val store=(vm.getApplication<android.app.Application>() as br.com.diariodeclasse.DiaryApplication).selectionStore
                store.write(resolveSelection(store.read(),active))
            }
        },
        archived=data.classArchives.any{it.classId==editingClass?.id},
        reports={classForm=false;detail="archived-reports"},
        export={classForm=false;detail="archived-export"},
        records={classForm=false;detail="archived-records"}) { id ->
        classForm = false
        // O formulário retorna o ano persistido, não a posição da turma no seletor.
        val wasArchived=data.classArchives.any{it.classId==id}
        vm.act(done = { page = 0; detail = if(wasArchived) "archives" else "roster" }) {
            val klass = requireNotNull(vm.repository.dao.schoolClass(id))
            if(!wasArchived) (vm.getApplication<android.app.Application>() as br.com.diariodeclasse.DiaryApplication).selectionStore.write(AppSelection(klass.year, id))
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
        Row(Modifier.padding(NoctuaSpace.small), horizontalArrangement = Arrangement.spacedBy(NoctuaSpace.small),verticalAlignment=Alignment.CenterVertically) {
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
                        Text(klass?.name ?: "Selecionar turma",style=MaterialTheme.typography.titleSmall)
                        if (klass != null) Text(klass.subject, style = MaterialTheme.typography.labelSmall)
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

@Composable private fun ClassManagementScreen(data: DiarySnapshot, c: SchoolClass?, year: Int, create: () -> Unit, edit: () -> Unit, roster: () -> Unit, archives: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(NoctuaSpace.page), verticalArrangement = Arrangement.spacedBy(NoctuaSpace.page)) {
        item {
            Section("Cadastrar Turma", "Organize suas turmas e seus estudantes")
            Button(create, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("+ Nova Turma") }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(edit, enabled = c != null, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("edit-class")) { Text("Editar Turma") }
        }
        item { TextButton(onClick=archives,modifier=Modifier.testTag("open-archives")){Text("Turmas Arquivadas")} }
        if (c == null) item { Text("Nenhuma turma selecionada em $year.") }
        else {
            item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(c.name, style = MaterialTheme.typography.titleLarge)
                Text("${c.stage} • ${c.shift}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider()
                Text("${counted(data.roster(c.id).count { it.status == "Ativo" }, "aluno ativo", "alunos ativos")} • ${counted(data.roster(c.id).size, "matrícula", "matrículas")}", style = MaterialTheme.typography.titleMedium)
                Button(roster, Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("Gerenciar alunos") }
            } } }
            val drafts = data.drafts.count { it.classId == c.id }
            if (drafts > 0) item { OutlinedCard(onClick = roster, modifier = Modifier.fillMaxWidth()) { Text("${counted(drafts, "aluno para conferir", "alunos para conferir")}", Modifier.padding(18.dp)) } }
        }
    }
}

@Composable private fun EmptyClassPage(title: String, create: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(NoctuaSpace.section), verticalArrangement = Arrangement.spacedBy(NoctuaSpace.page)) {
        Section(title, "Escolha uma turma para continuar")
        Info("Selecione ou cadastre uma turma.")
        Button(create, Modifier.fillMaxWidth()) { Text("Nova Turma") }
    }
}

@Composable private fun ActivitiesPage(vm: DiaryViewModel, data: DiarySnapshot, c: SchoolClass, term: Int, create: () -> Unit, recomposition: () -> Unit, edit: (Activity) -> Unit, grades: (String) -> Unit) {
    val activities = data.activities.filter { it.classId == c.id && it.term == term }
    var removing by remember { mutableStateOf<Activity?>(null) }
    LazyColumn(Modifier.testTag("activities-list"), contentPadding = PaddingValues(NoctuaSpace.page), verticalArrangement = Arrangement.spacedBy(NoctuaSpace.medium)) {
        item { Button(create, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("+ Criar atividade") } }
        item { OutlinedButton(recomposition, Modifier.fillMaxWidth().testTag("open-recomposition")) { Text("Recomposição de Notas") } }
        if (activities.isEmpty()) item { Text("Nenhuma atividade registrada neste bimestre.") }
        items(activities, key = { it.id }) { a -> Card(Modifier.fillMaxWidth().testTag("activity-${a.id}")) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(a.name, Modifier.clickable { grades(a.id) }, style = MaterialTheme.typography.titleMedium)
                Text("${a.type} • ${shortDate(a.date)} • ${number(a.maximum)} pontos")
                val pending = data.currentRoster(c.id).filter { it.enrolled(a.date) }.count { s -> data.grades.none { it.activityId == a.id && it.studentId == s.id && it.value != null } }
                Text("${counted(pending, "nota pendente", "notas pendentes")}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { grades(a.id) }, modifier = Modifier.testTag("activity-grades-${a.id}"), contentPadding = PaddingValues(NoctuaSpace.tiny)) { Text("Lançar notas") }
                    TextButton(onClick = { edit(a) }, modifier = Modifier.testTag("activity-edit-${a.id}"), contentPadding = PaddingValues(NoctuaSpace.tiny)) { Text("Editar") }
                    TextButton(onClick = { removing = a }, modifier = Modifier.testTag("activity-delete-${a.id}"), contentPadding = PaddingValues(NoctuaSpace.tiny)) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
                }
            }
        } }
    }
    removing?.let { a -> DeleteActivityDialog(vm, a.id, { removing = null }) { removing = null } }
}

@Composable fun TermSelector(selected: Int, select: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = NoctuaSpace.page), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..4).forEach { n -> FilterChip(modifier = Modifier.weight(1f), selected = selected == n,
            onClick = { if (selected != n) select(n) }, label = { Text("${n}º bim.", maxLines = 1, style = MaterialTheme.typography.labelSmall) }) }
    }
}
