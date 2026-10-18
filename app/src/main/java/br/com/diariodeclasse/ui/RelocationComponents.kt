package br.com.diariodeclasse.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import br.com.diariodeclasse.data.*

@Composable fun RelocatedFilter(value: Boolean, change: (Boolean) -> Unit) {
    Row {Checkbox(value,change,modifier=Modifier.testTag("show-relocated"));Text("Mostrar remanejados (histórico)",Modifier.padding(top=12.dp),style=MaterialTheme.typography.bodySmall)}
}
@Composable fun RelocationNotice(source: DiarySnapshot, student: Student) {
    val data=source.academic()
    data.relocations.firstOrNull {it.sourceStudentId==student.id}?.let { link ->
        val target=link.externalTarget ?: data.students.firstOrNull {it.id==link.targetStudentId}?.let {target ->data.classes.firstOrNull {it.id==target.classId}?.name} ?: "Destino indisponível"
        Text("Remanejado para $target em ${shortDate(link.effectiveDate)} • histórico preservado",Modifier.testTag("relocation-notice-${student.id}"),style=MaterialTheme.typography.bodySmall)
    }
    val chain=data.lineage(student)
    if(chain.size>1) Text("Histórico consolidado: ${chain.joinToString(" → ") {enrollment->data.classes.firstOrNull {it.id==enrollment.classId}?.name?:enrollment.classId}}",style=MaterialTheme.typography.bodySmall)
}

fun relocationLabel(source: DiarySnapshot, student: Student): String {
    val data=source.academic()
    val link=data.relocations.firstOrNull {it.sourceStudentId==student.id} ?: return student.status
    val target=link.externalTarget ?: data.students.firstOrNull {it.id==link.targetStudentId}?.let {s->data.classes.firstOrNull {it.id==s.classId}?.name} ?: "Destino indisponível"
    return "Remanejado → $target • ${shortDate(link.effectiveDate)}"
}
fun LazyListScope.relocationHistory(source: DiarySnapshot, student: Student) {
    val data=source.academic()
    val chain=data.lineage(student)
    if(chain.size<=1 && data.relocations.none {it.sourceStudentId==student.id}) return
    item {Section("Origem dos registros", "Matrículas e horários originais; consulta somente leitura")}
    items(chain,key={"enrollment-${it.id}"}) { enrollment ->
        Text("${data.classes.firstOrNull {it.id==enrollment.classId}?.name ?: enrollment.classId}${if(source.histories.any {it.classId==enrollment.classId}) " (turma excluída)" else ""} • nº ${enrollment.number}\n${shortDate(enrollment.joined)} a ${enrollment.left?.let(::shortDate) ?: "sem saída"} • ${relocationLabel(data,enrollment)}",Modifier.testTag("history-enrollment-${enrollment.id}"))
    }
    val school=data.classes.first {it.id==student.classId}
    val history=br.com.diariodeclasse.reports.SchoolReports(data,school).attendanceHistory(student)
    items(history,key={"history-${it.enrollment.id}-${it.lesson.id}"}) { entry ->
        Text("${shortDate(entry.lesson.date)} • ${entry.lesson.ordinal}º horário • ${entry.school.name}\n${entry.mark ?: "NR"} • ${entry.lesson.state}${if(!entry.eligible) " • fora da matrícula, não contabilizado" else ""}",Modifier.testTag("history-attendance-${entry.lesson.id}"),style=MaterialTheme.typography.bodySmall)
    }
}
