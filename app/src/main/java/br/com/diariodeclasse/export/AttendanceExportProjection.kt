package br.com.diariodeclasse.export

import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.reports.SchoolReports
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Chamada por horário; usa o mesmo histórico elegível dos relatórios, sem gravar nada. */
object AttendanceExportProjection {
    fun build(data:DiarySnapshot,c:SchoolClass,start:String,end:String):ExportTable {
        val first=LocalDate.parse(start);val last=LocalDate.parse(end)
        require(first<=last && first.year==c.year && last.year==c.year) {"Informe um período válido dentro do ano letivo da turma."}
        require(data.classes.any{it.id==c.id})
        val included=ExportLayout.project(data,ExportLayout.rows(data,c.id)).filter{it.included}.mapNotNull{it.student?.id}.toSet()
        val students=data.currentRoster(c.id).filter{it.id in included}.sortedWith(compareBy<Student>{it.number}.thenBy{it.name}.thenBy{it.id})
        val reports=SchoolReports(data,c)
        val histories=students.associate{student->student.id to reports.attendanceHistory(student).filter{
            it.eligible && it.lesson.state=="REALIZADA" && it.lesson.date in start..end
        }.associateBy{it.lesson.id}}
        val lessons=histories.values.flatMap{it.values}.distinctBy{it.lesson.id}.sortedWith(
            compareBy<br.com.diariodeclasse.reports.AttendanceHistory>{it.lesson.date}.thenBy{it.lesson.ordinal}.thenBy{it.school.id}.thenBy{it.lesson.id})
        val date=DateTimeFormatter.ofPattern("dd/MM")
        val columns=listOf(ExportColumn("number","Nº da chamada"),ExportColumn("name","Nome completo"))+lessons.map{
            ExportColumn("lesson:${it.lesson.id}","${LocalDate.parse(it.lesson.date).format(date)} • ${it.lesson.ordinal}º horário"+
                if(it.school.id!=c.id) " • ${it.school.name} (origem)" else "")
        }
        val records=students.map{student->ExportRecord(student.id,listOf(ExportCell.number(student.number,NumberStyle.INTEGER),ExportCell.text(student.name))+lessons.map{
            histories.getValue(student.id)[it.lesson.id]?.mark?.takeIf{it in setOf("P","F","FJ")}?.let(ExportCell::text) ?: ExportCell.EMPTY
        })}
        return ExportTable("Noctua • ${c.name} • ${c.subject} • ${c.year} • Chamada • $start a $end",
            ExportOptions(model=ExportModel.FREQUENCY,term=null),columns,records)
    }
}
