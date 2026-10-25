package br.com.diariodeclasse.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.domain.RosterParser
import java.time.LocalDate

@Composable fun ClassForm(vm: DiaryViewModel, original: SchoolClass?, selectedYear: Int, dismiss: () -> Unit, administrativeFinished: (String) -> Unit,
    archived: Boolean = false, reports: () -> Unit = {}, export: () -> Unit = {}, records: () -> Unit = {}, saved: (String) -> Unit) {
    val c = remember { original ?: SchoolClass(name = "", subject = "", year = selectedYear) }
    var name by rememberSaveable { mutableStateOf(c.name) }; var subject by rememberSaveable { mutableStateOf(c.subject) }
    var year by rememberSaveable { mutableStateOf(c.year.toString()) }; var stage by rememberSaveable { mutableStateOf(c.stage) }
    var shift by rememberSaveable { mutableStateOf(c.shift) }; var advanced by rememberSaveable { mutableStateOf(false) }
    var duration by rememberSaveable { mutableStateOf(c.duration.toString()) }
    val schedule = remember { mutableStateListOf(*c.schedule.split(',').toTypedArray()) }
    var weekly by rememberSaveable { mutableStateOf(false) }
    var effective by rememberSaveable { mutableStateOf(shortDate(today().coerceIn("${c.year}-01-01", "${c.year}-12-31"))) }
    var scale by rememberSaveable { mutableStateOf(c.scale.toString().replace('.', ',')) }; var low by rememberSaveable { mutableStateOf(c.low.toString().replace('.', ',')) }
    var high by rememberSaveable { mutableStateOf(c.high.toString().replace('.', ',')) }; var countFj by rememberSaveable { mutableStateOf(c.countJustified) }
    var present by rememberSaveable { mutableStateOf(c.startPresent) }
    val saving by vm.saving.collectAsState()
    Form(if (original == null) "Nova Turma" else "Editar Turma", { if(!saving) dismiss() }) {
        Text("Dados da turma", style = MaterialTheme.typography.titleMedium)
        Field("Nome da turma", name, { name = it }); Field("Disciplina", subject, { subject = it })
        if (original == null) Field("Ano letivo", year, { year = it })
        else Text("Ano letivo ${c.year} • preservado com o histórico", style = MaterialTheme.typography.bodyMedium)
        Choice("Etapa", listOf("Fundamental", "Médio"), stage) { stage = it }
        Choice("Turno", listOf("Matutino", "Vespertino", "Noturno"), shift) { shift = it }
        TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Ocultar configurações adicionais" else "Configurações adicionais (opcional)") }
        if (advanced) {
            Field("Duração de cada aula (min)", duration, { duration = it })
            Field("Escala final", scale, { scale = it }); Field("Abaixo desta nota: vermelho", low, { low = it })
            Field("A partir desta nota: verde", high, { high = it })
            Row { Checkbox(countFj, { countFj = it }); Text("Falta justificada entra como ausência na frequência", Modifier.padding(top = 12.dp)) }
            Row { Checkbox(present, { present = it }); Text("Iniciar novas chamadas com todos presentes", Modifier.padding(top = 12.dp)) }
            TextButton(onClick = { weekly = !weekly }) { Text("Horários semanais legados (informação)") }
            if (weekly) {
                Info("Horários informativos; não geram aulas.")
                listOf("Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado", "Domingo").forEachIndexed { i, day -> Field(day, schedule[i], { schedule[i] = it }) }
                if (original != null) Field("Referência do horário legado (dd/mm/aaaa)", effective, { effective = it })
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
        SaveError(vm)
        if(original!=null) ClassAdministrativeActions(vm,c,archived,administrativeFinished,reports,export,records)
    }
}

@Composable fun TermForm(vm: DiaryViewModel, c: SchoolClass, terms: List<Term>, dismiss: () -> Unit) {
    val starts = remember { mutableStateListOf(*terms.sortedBy { it.number }.map { shortDate(it.start) }.toTypedArray()) }
    val ends = remember { mutableStateListOf(*terms.sortedBy { it.number }.map { shortDate(it.end) }.toTypedArray()) }
    var preview by remember { mutableStateOf<List<Term>?>(null) }
    Form("Configurar bimestres", dismiss) {
        if (starts.size == 4) for (i in 0..3) {
            Text("${i + 1}º bimestre", style = MaterialTheme.typography.titleMedium)
            Field("Início (dd/mm/aaaa)", starts[i], { starts[i] = it })
            Field("Término (dd/mm/aaaa)", ends[i], { ends[i] = it })
        }
        Button(onClick = {
            val a = starts.toList(); val b = ends.toList()
            vm.act {
                val proposed = (0..3).map { Term(c.id, it + 1, iso(a[it]), iso(b[it])) }
                vm.repository.validateTerms(c, proposed)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { preview = proposed }
            }
        }) { Text("Salvar calendário") }
        SaveError(vm)
    }
    preview?.let { proposed ->
        val moved = vm.data.value.lessons.count { lesson -> lesson.classId == c.id && lesson.state == "REALIZADA" &&
            terms.firstOrNull { lesson.date in it.start..it.end }?.number != proposed.firstOrNull { lesson.date in it.start..it.end }?.number }
        Confirm("Recalcular períodos da frequência?", "$moved aulas realizadas mudarão de classificação bimestral. Nenhuma aula ou chamada será apagada; os totais serão recalculados pelas novas datas. Atividades e notas mantêm seus bimestres originais.", { preview = null }) {
            vm.act(done = dismiss) { vm.repository.saveTerms(c.id, proposed) }
        }
    }
}

@Composable fun StudentForm(vm: DiaryViewModel, c: SchoolClass, original: Student?, initialStatus: String? = null, deleted: () -> Unit = {}, dismiss: () -> Unit) {
    val data by vm.data.collectAsState()
    val saving by vm.saving.collectAsState()
    val student = remember { original ?: Student(classId = c.id, name = "", number = (data.roster(c.id).maxOfOrNull { it.number } ?: 0) + 1, joined = defaultEnrollment(c.year)) }
    var name by rememberSaveable { mutableStateOf(student.name) }; var num by rememberSaveable { mutableStateOf(student.number.toString()) }
    var joined by rememberSaveable { mutableStateOf(shortDate(student.joined)) }; var left by rememberSaveable { mutableStateOf(student.left?.let { shortDate(it) } ?: "") }
    var status by rememberSaveable { mutableStateOf(initialStatus ?: student.status) }; var notes by rememberSaveable { mutableStateOf(student.notes) }
    var homonym by rememberSaveable(name) { mutableStateOf(false) }
    var target by rememberSaveable { mutableStateOf("") };var external by rememberSaveable { mutableStateOf("") }
    var effective by rememberSaveable { mutableStateOf(shortDate(today())) }
    var preview by remember { mutableStateOf<RelocationPreview?>(null) }
    var deleting by remember(student.id,c.id) { mutableStateOf(false) }
    var deletionPreview by remember(student.id,c.id) { mutableStateOf<StudentDeletionPreview?>(null) }
    val deletionError by vm.error.collectAsState()
    var discard by remember { mutableStateOf(false) }
    var confirmReactivation by remember { mutableStateOf(false) }
    var correcting by remember { mutableStateOf(false) }
    if (correcting) { RelocationCorrectionForm(vm, student) { correcting = false; dismiss() }; return }
    val linked=data.academic().relocations.any {it.sourceStudentId==student.id||it.targetStudentId==student.id}
    val relocating=status=="Remanejado" && student.status=="Ativo" && original!=null
    val reactivating=student.status=="Transferido" && status=="Ativo"
    val duplicate = data.roster(c.id).any { it.id != student.id && RosterParser.possibleDuplicate(it.name, name) }
    val dirty=name!=student.name||num!=student.number.toString()||joined!=shortDate(student.joined)||left!=(student.left?.let(::shortDate)?:"")||status!=student.status||notes!=student.notes||target.isNotBlank()||external.isNotBlank()
    fun leave() {if(!saving) {if(dirty) discard=true else dismiss()}}
    if(preview==null) Form(if (original == null) "Cadastrar aluno" else "Editar aluno", ::leave) {
        if(student.status=="Ativo" && student.left!=null && !linked) {
            Info("Esta matrícula está Ativa, mas possui saída em ${shortDate(student.left)}. Se foi reativada, confirme abaixo para retirar essa saída antes de remanejar. Se a saída é legítima, mantenha-a.")
            OutlinedButton(enabled=!saving&&!dirty,onClick={confirmReactivation=true},modifier=Modifier.testTag("repair-reactivation")) {Text("Confirmar reativação")}
            if(dirty) Text("Para confirmar a reativação, reabra o cadastro sem outras alterações pendentes.",style=MaterialTheme.typography.bodySmall)
        }
        Field("Nome completo", name, { if(!saving) name = it }); Field("Número na chamada", num, { if(!saving) num = it })
        Field("Ingresso (dd/mm/aaaa)", joined, { joined = it }, enabled=!linked&&!saving&&!relocating&&!reactivating)
        if(!relocating) Field("Saída (opcional, dd/mm/aaaa)", left, { left = it }, enabled=!linked&&!saving&&!reactivating)
        if (student.status == "Remanejado" && linked) OutlinedButton(enabled = !saving && !dirty, onClick = { correcting = true }, modifier = Modifier.testTag("correct-relocation")) { Text("Corrigir remanejamento") }
        if(student.status=="Remanejado" && linked) Info("Matrícula remanejada: situação e datas preservadas. Consulte o destino e o histórico; este formulário não reativa a matrícula.")
        else Choice("Situação", if(linked&&student.status=="Ativo") listOf("Ativo", "Remanejado") else if(original!=null&&student.status=="Ativo") listOf("Ativo", "Transferido", "Remanejado") else listOf("Ativo", "Transferido",student.status).distinct(), status) { if(!saving) status = it }
        Field("Observações pedagógicas", notes, { if(!saving) notes = it }, lines = 3)
        if(reactivating) Info("Ao salvar como Ativo, a saída anterior será retirada. A mesma matrícula e todos os registros escolares serão preservados. Não reative se a transferência continua válida.")
        if(relocating) {
            Field("Data efetiva do remanejamento (dd/mm/aaaa)", effective, {if(!saving) effective=it},Modifier.testTag("relocation-date"))
            Text("Turma de destino",style=MaterialTheme.typography.titleSmall)
            val targets=data.classes.filter {school->data.classArchives.none{it.classId==school.id}&&RelocationRules.compatible(c,school,data.terms)}
            targets.forEach { school -> Row {
                RadioButton(target==school.id,{if(!saving) target=school.id},enabled=!saving,modifier=Modifier.testTag("relocation-target-${school.id}"))
                Text("${school.name} • ${school.subject} • ${school.year}",Modifier.padding(top=12.dp))
            }}
            if(targets.isEmpty()) Text("Não há outra turma compatível. Confira ano, disciplina, etapa, escala, bimestres e política de FJ.")
            Row {RadioButton(target=="@external",{if(!saving) target="@external"},enabled=!saving,modifier=Modifier.testTag("relocation-external"));Text("Outra turma",Modifier.padding(top=12.dp))}
            if(target=="@external") Field("Nome da turma de destino",external,{if(!saving)external=it},Modifier.testTag("relocation-external-name"))
            Info("A origem será encerrada no dia anterior. Notas e chamadas permanecem nos registros originais e serão consultadas por vínculo no destino cadastrado. Nenhum dado muda antes da confirmação da prévia.")
        } else Info("Datas de ingresso e saída limitam a frequência.")
        if (duplicate) {
            Info("Há um nome semelhante nesta turma. Confira se já está cadastrado.")
            Row { Checkbox(homonym, { homonym = it }); Text("Confirmo que é outra pessoa (homônimo)", Modifier.padding(top = 12.dp)) }
        }
        StudentFormActions(saveEnabled = !saving && (!duplicate || homonym) && (!relocating || target.isNotBlank()),
            saveLabel = if(relocating) "Conferir remanejamento" else "Salvar aluno", save = {
            val edited=student.copy(name=name.trim(),number=num.toIntOrNull()?:0,notes=notes)
            val formJoined=joined;val formLeft=left;val formStatus=status;val formDay=effective;val formTarget=target;val formExternal=external
            vm.act {
                if(relocating) {
                    require(iso(formJoined)==student.joined && formLeft==(student.left?.let(::shortDate)?:"")) {"Confirme separadamente qualquer correção de ingresso/saída antes de remanejar. As datas digitadas não serão ignoradas."}
                    val p=vm.repository.previewRelocation(student.id,formTarget.takeUnless {it=="@external"},formExternal.takeIf {formTarget=="@external"},iso(formDay),edited)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { preview=p }
                } else {
                    vm.repository.saveStudent(edited.copy(joined=iso(formJoined),left=formLeft.takeIf {it.isNotBlank()}?.let(::iso),status=formStatus))
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {dismiss()}
                }
            }
        }, deleteEnabled = !saving, delete = if(original==null) null else ({
            vm.clearError(); deletionPreview=null; deleting=true
            val id=student.id; val classId=c.id
            vm.act {
                val plan=vm.repository.previewStudentDeletion(id,classId)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { deletionPreview=plan }
            }
        }))
        SaveError(vm)
    }
    if(deleting) StudentDeletionDialog(deletionPreview,saving,deletionError,
        cancel={deleting=false;deletionPreview=null;vm.clearError()},
        confirm={
            val plan=deletionPreview
            if(plan!=null&&!saving) vm.act(done={
                deleting=false;deletionPreview=null;dismiss();deleted();vm.message("Aluno excluído desta turma.")
            }) {
                try { vm.repository.deleteStudent(plan) }
                catch(e:kotlinx.coroutines.CancellationException) { throw e }
                catch(e:IllegalArgumentException) { throw e }
                catch(e:Exception) { throw IllegalArgumentException("Não foi possível excluir. Nenhum registro foi alterado.",e) }
            }
        })
    preview?.let { p -> Form("Confirmar remanejamento",{if(!saving)preview=null}) {
        Text(p.proposedSource.name,style=MaterialTheme.typography.titleMedium)
        Info("Origem: ${c.name} • matrícula encerrada em ${shortDate(p.end)}\nDestino: ${p.targetName} • ingresso em ${shortDate(p.link.effectiveDate)}\n${if(p.target==null) "Destino externo: sem criar turma ou matrícula fictícia." else if(p.linkedExisting) "Será vinculada a matrícula existente, com a mesma identidade." else "Será criada uma matrícula ativa com a mesma identidade da pessoa."}")
        Text("${p.gradeCount} notas lançadas e ${p.markCount} marcações anteriores preservadas nos IDs originais. Zero permanece zero; pendências permanecem pendentes. Na origem, o aluno ficará em Mostrar remanejados, somente para consulta dos registros escolares.")
        if(p.target!=null) Text("Pontos elegíveis do mesmo bimestre serão somados às atividades do destino e limitados à escala existente. Recomposição permanece separada; aulas não são convertidas para a grade do destino.")
        Button(enabled=!saving,onClick={vm.act(done=dismiss) {vm.repository.confirmRelocation(p)}},modifier=Modifier.fillMaxWidth().testTag("relocation-confirm")) {Text(if(saving) "Salvando…" else "Confirmar remanejamento")}
        OutlinedButton(enabled=!saving,onClick={preview=null},modifier=Modifier.fillMaxWidth().testTag("relocation-cancel")) {Text("Cancelar remanejamento")}
        SaveError(vm)
    }}
    if(discard) Confirm("Descartar alterações não salvas?","Nenhum remanejamento ou alteração de cadastro será realizado.",{discard=false}) {discard=false;dismiss()}
    if(confirmReactivation) Confirm("Reativar esta matrícula?","A saída em ${student.left?.let(::shortDate)} será retirada da matrícula de ${student.name}. Confirme apenas se ela voltou legitimamente à turma. IDs, observações, notas, chamadas e recomposições serão preservados. Nenhuma presença ou falta será criada.",{if(!saving)confirmReactivation=false}) {
        if(!saving) {confirmReactivation=false;vm.act(done=dismiss) {vm.repository.repairReactivatedStudent(student)}}
    }
}

@Composable fun ActivityForm(vm: DiaryViewModel, c: SchoolClass, term: Int, original: Activity?, dismiss: () -> Unit, saved: (Activity) -> Unit) {
    val data by vm.data.collectAsState()
    val period = data.terms.firstOrNull { it.classId == c.id && it.number == term }
    val a = remember { original ?: Activity(classId = c.id, name = "", date = today().coerceIn(period?.start ?: today(), period?.end ?: today()), term = term, maximum = 10.0) }
    var name by rememberSaveable { mutableStateOf(a.name) }; var type by rememberSaveable { mutableStateOf(a.type) }
    var day by rememberSaveable { mutableStateOf(shortDate(a.date)) }; var max by rememberSaveable { mutableStateOf(editableNumber(a.maximum)) }
    var description by rememberSaveable { mutableStateOf(a.description) }; var notes by rememberSaveable { mutableStateOf(a.notes) }
    var selectedTerm by rememberSaveable { mutableStateOf(a.term.toString()) }
    var preview by remember { mutableStateOf<ActivityEditPreview?>(null) }
    val saving by vm.saving.collectAsState()
    if (preview == null) Form(if (original == null) "Criar atividade" else "Editar atividade", { if (!saving) dismiss() }) {
        Field("Nome da atividade", name, { name = it })
        Choice("Tipo", listOf("Prova", "Trabalho", "Redação", "Atividade prática", "Seminário", "Estudo dirigido", "Outro"), type) { type = it }
        Field("Data (dd/mm/aaaa)", day, { day = it }); Choice("Bimestre", listOf("1", "2", "3", "4"), selectedTerm) { selectedTerm = it }
        Field("Pontuação máxima", max, { max = it }); Field("Descrição", description, { description = it }, lines = 2)
        Field("Observações", notes, { notes = it }, lines = 2)
        Button(enabled = !saving, onClick = {
            val formName=name;val formType=type;val formDay=day;val formTerm=selectedTerm;val formMax=max;val formDescription=description;val formNotes=notes
            vm.act {
                val value=a.copy(name=formName.trim(),type=formType,date=iso(formDay),term=formTerm.toInt(),maximum=requiredDecimal(formMax),description=formDescription,notes=formNotes)
                if (original == null) {
                    vm.repository.saveActivity(value)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { saved(value) }
                } else {
                    val proposed=vm.repository.previewActivityEdit(value,a)
                    if (proposed.needsChoice) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { preview=proposed }
                    else {
                        vm.repository.applyActivityEdit(proposed,GradeChangeMode.KEEP)
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { saved(value) }
                    }
                }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text(if (original == null) "Salvar e lançar notas" else "Salvar alterações") }
        SaveError(vm)
    }
    preview?.let { proposed -> GradeChangeDialog(vm,proposed,{ preview=null }) { mode ->
        vm.act(done={ saved(proposed.proposed) }) { vm.repository.applyActivityEdit(proposed,mode) }
    } }
}
