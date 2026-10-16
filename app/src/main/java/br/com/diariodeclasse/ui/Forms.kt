package br.com.diariodeclasse.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.domain.RosterParser
import java.time.LocalDate

@Composable fun ClassForm(vm: DiaryViewModel, original: SchoolClass?, selectedYear: Int, dismiss: () -> Unit, saved: (String) -> Unit) {
    val c = remember { original ?: SchoolClass(name = "", subject = "", year = selectedYear) }
    var name by rememberSaveable { mutableStateOf(c.name) }; var subject by rememberSaveable { mutableStateOf(c.subject) }
    var year by rememberSaveable { mutableStateOf(c.year.toString()) }; var stage by rememberSaveable { mutableStateOf(c.stage) }
    var shift by rememberSaveable { mutableStateOf(c.shift) }; var advanced by rememberSaveable { mutableStateOf(false) }
    var duration by rememberSaveable { mutableStateOf(c.duration.toString()) }
    val schedule = remember { mutableStateListOf(*c.schedule.split(',').toTypedArray()) }
    var weekly by rememberSaveable { mutableStateOf(false) }
    var effective by rememberSaveable { mutableStateOf(shortDate(today().coerceIn("${c.year}-01-01", "${c.year}-12-31"))) }
    var scale by rememberSaveable { mutableStateOf(number(c.scale)) }; var low by rememberSaveable { mutableStateOf(number(c.low)) }
    var high by rememberSaveable { mutableStateOf(number(c.high)) }; var countFj by rememberSaveable { mutableStateOf(c.countJustified) }
    var present by rememberSaveable { mutableStateOf(c.startPresent) }
    val saving by vm.saving.collectAsState()
    Form(if (original == null) "Nova Turma" else "Editar Turma Existente", dismiss) {
        Text("Dados da turma", style = MaterialTheme.typography.titleMedium)
        Field("Nome da turma", name, { name = it }); Field("Disciplina", subject, { subject = it })
        if (original == null) Field("Ano letivo", year, { year = it })
        else Text("Ano letivo ${c.year} • preservado com o histórico", style = MaterialTheme.typography.bodyMedium)
        Choice("Etapa", listOf("Fundamental", "Médio"), stage) { stage = it }
        Choice("Turno", listOf("Matutino", "Vespertino", "Noturno"), shift) { shift = it }
        Info("Nenhum horário semanal é obrigatório. A turma fica disponível durante todo o ano letivo. Cadastre os alunos depois de salvar.")
        TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Ocultar configurações adicionais" else "Configurações adicionais (opcional)") }
        if (advanced) {
            Field("Duração de cada aula (min)", duration, { duration = it })
            Field("Escala final", scale, { scale = it }); Field("Abaixo desta nota: vermelho", low, { low = it })
            Field("A partir desta nota: verde", high, { high = it })
            Row { Checkbox(countFj, { countFj = it }); Text("Falta justificada entra como ausência na frequência", Modifier.padding(top = 12.dp)) }
            Row { Checkbox(present, { present = it }); Text("Iniciar novas chamadas com todos presentes", Modifier.padding(top = 12.dp)) }
            TextButton(onClick = { weekly = !weekly }) { Text("Horários semanais opcionais") }
            if (weekly) {
                Info("Configuração anterior preservada. Altere somente se quiser gerar aulas previstas. Chamadas realizadas não serão apagadas.")
                listOf("Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado", "Domingo").forEachIndexed { i, day -> Field(day, schedule[i], { schedule[i] = it }) }
                if (original != null) Field("Novo horário a partir de (dd/mm/aaaa)", effective, { effective = it })
            }
        }
        Button(enabled = !saving, onClick = {
            val formName = name; val formSubject = subject; val formYear = year; val formStage = stage; val formShift = shift
            val formDuration = duration; val formSchedule = schedule.joinToString(","); val formScale = scale; val formLow = low; val formHigh = high
            val formFj = countFj; val formPresent = present; val formEffective = effective
            vm.act(done = { saved(c.id) }) {
                val value = c.copy(name = formName.trim(), subject = formSubject.trim(), year = formYear.toInt(), stage = formStage,
                    shift = formShift, duration = formDuration.toInt(), schedule = formSchedule, scale = requiredDecimal(formScale),
                    low = requiredDecimal(formLow), high = requiredDecimal(formHigh), countJustified = formFj, startPresent = formPresent)
                vm.repository.saveClass(value, if (original == null || formSchedule == c.schedule) "${value.year}-01-01" else iso(formEffective))
            }
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("Salvar turma") }
    }
}

@Composable fun TermForm(vm: DiaryViewModel, c: SchoolClass, terms: List<Term>, dismiss: () -> Unit) {
    val starts = remember { mutableStateListOf(*terms.sortedBy { it.number }.map { shortDate(it.start) }.toTypedArray()) }
    val ends = remember { mutableStateListOf(*terms.sortedBy { it.number }.map { shortDate(it.end) }.toTypedArray()) }
    Form("Configurar bimestres", dismiss) {
        Info("Datas iniciais são sugestões. Ajuste ao calendário da sua rede. Períodos não podem se sobrepor.")
        if (starts.size == 4) for (i in 0..3) {
            Text("${i + 1}º bimestre", style = MaterialTheme.typography.titleMedium)
            Field("Início (dd/mm/aaaa)", starts[i], { starts[i] = it })
            Field("Término (dd/mm/aaaa)", ends[i], { ends[i] = it })
        }
        Button(onClick = { vm.act(done = dismiss) { vm.repository.saveTerms(c.id, (0..3).map { Term(c.id, it + 1, iso(starts[it]), iso(ends[it])) }) } }) { Text("Salvar calendário") }
    }
}

@Composable fun StudentForm(vm: DiaryViewModel, c: SchoolClass, original: Student?, dismiss: () -> Unit) {
    val data by vm.data.collectAsState()
    val student = remember { original ?: Student(classId = c.id, name = "", number = (data.roster(c.id).maxOfOrNull { it.number } ?: 0) + 1,
        joined = defaultEnrollment(c.year)) }
    var name by rememberSaveable { mutableStateOf(student.name) }; var num by rememberSaveable { mutableStateOf(student.number.toString()) }
    var joined by rememberSaveable { mutableStateOf(shortDate(student.joined)) }; var left by rememberSaveable { mutableStateOf(student.left?.let { shortDate(it) } ?: "") }
    var status by rememberSaveable { mutableStateOf(student.status) }; var notes by rememberSaveable { mutableStateOf(student.notes) }
    var homonym by rememberSaveable(name) { mutableStateOf(false) }
    val duplicate = data.roster(c.id).any { it.id != student.id && RosterParser.possibleDuplicate(it.name, name) }
    Form(if (original == null) "Cadastrar aluno" else "Editar aluno", dismiss) {
        Field("Nome completo", name, { name = it }); Field("Número na chamada", num, { num = it })
        Field("Ingresso (dd/mm/aaaa)", joined, { joined = it }); Field("Saída (opcional, dd/mm/aaaa)", left, { left = it })
        Choice("Situação", listOf("Ativo", "Transferido", "Remanejado"), status) { status = it }
        Field("Observações pedagógicas", notes, { notes = it }, lines = 3)
        Info("Use a data real de ingresso. Chamadas anteriores não serão contabilizadas para este aluno.")
        if (duplicate) {
            Info("Há um nome semelhante nesta turma. Confira se já está cadastrado.")
            Row { Checkbox(homonym, { homonym = it }); Text("Confirmo que é outra pessoa (homônimo)", Modifier.padding(top = 12.dp)) }
        }
        Button(enabled = !duplicate || homonym, onClick = {
            val formName = name; val formNumber = num; val formJoined = joined; val formLeft = left; val formStatus = status; val formNotes = notes
            vm.act(done = dismiss) { vm.repository.saveStudent(student.copy(name = formName, number = formNumber.toInt(), joined = iso(formJoined), left = formLeft.takeIf { it.isNotBlank() }?.let { iso(it) }, status = formStatus, notes = formNotes)) }
        }) { Text("Salvar aluno") }
    }
}

@Composable fun ActivityForm(vm: DiaryViewModel, c: SchoolClass, term: Int, original: Activity?, dismiss: () -> Unit, saved: (String) -> Unit) {
    val data by vm.data.collectAsState()
    val period = data.terms.firstOrNull { it.classId == c.id && it.number == term }
    val a = remember { original ?: Activity(classId = c.id, name = "", date = today().coerceIn(period?.start ?: today(), period?.end ?: today()), term = term, maximum = 10.0) }
    var name by rememberSaveable { mutableStateOf(a.name) }; var type by rememberSaveable { mutableStateOf(a.type) }
    var day by rememberSaveable { mutableStateOf(shortDate(a.date)) }; var max by rememberSaveable { mutableStateOf(number(a.maximum)) }
    var description by rememberSaveable { mutableStateOf(a.description) }; var notes by rememberSaveable { mutableStateOf(a.notes) }
    var selectedTerm by rememberSaveable { mutableStateOf(a.term.toString()) }
    Form(if (original == null) "Criar atividade" else "Editar atividade", dismiss) {
        Field("Nome da atividade", name, { name = it })
        Choice("Tipo", listOf("Prova", "Trabalho", "Redação", "Atividade prática", "Seminário", "Estudo dirigido", "Outro"), type) { type = it }
        Field("Data (dd/mm/aaaa)", day, { day = it }); Choice("Bimestre", listOf("1", "2", "3", "4"), selectedTerm) { selectedTerm = it }
        Field("Pontuação máxima", max, { max = it }); Field("Descrição", description, { description = it }, lines = 2)
        Field("Observações", notes, { notes = it }, lines = 2)
        Button(onClick = { vm.act(done = { saved(a.id) }) { vm.repository.saveActivity(a.copy(name = name, type = type, date = iso(day), term = selectedTerm.toInt(), maximum = requiredDecimal(max), description = description, notes = notes)) } }) { Text("Salvar e lançar notas") }
    }
}
