package br.com.diariodeclasse

import br.com.diariodeclasse.data.*
import org.junit.Assert.*
import org.junit.Test

class StudentDeletionTest {
    private val g=SchoolClass("delete-g",name="1º G fictício",subject="História",year=2026)
    private val h=g.copy(id="delete-h",name="1º H fictício")
    private val wrong=Student("wrong","person-wrong",h.id,"Estudante Fictício Homônimo",1,"2026-01-01",notes="Observação exclusiva")
    private val kept=wrong.copy(id="kept",personId="person-kept",classId=g.id,notes="Preservar integralmente")
    private val other=wrong.copy(id="other",personId="person-other",number=2,name="Outro Fictício")
    private val activity=Activity("delete-activity",h.id,"Atividade fictícia",date="2026-08-10",term=3,maximum=10.0)
    private fun fixture(): DiarySnapshot {
        val lessons=(1..4).map{Lesson("delete-lesson-$it",h.id,"2026-08-10",it,"REALIZADA",note="Observação da aula compartilhada")}
        return DiarySnapshot(classes=listOf(g,h),students=listOf(wrong,kept,other),activities=listOf(activity,activity.copy(id="pending")),
            grades=listOf(Grade(activity.id,wrong.id,0.0),Grade("pending",wrong.id,null),Grade(activity.id,other.id,8.0)),
            lessons=lessons,marks=lessons.mapIndexed{i,l->Attendance(l.id,wrong.id,listOf("P","F","FJ","NR")[i])}+Attendance(lessons[0].id,other.id,"FJ"),
            recompositions=listOf(Recomposition(wrong.id,3,0.0,"2026-08-20"),Recomposition(wrong.id,4,null,"2026-11-10")),
            exportRows=listOf(ExportRow("blank:initial",h.id,null,0),ExportLayout.studentRow(wrong,1),ExportLayout.studentRow(other,2),ExportLayout.studentRow(kept,0)))
    }
    @Test fun diagnosisCountsZeroPendingEachAttendanceAndOnlyRelevantCategories() {
        val d=fixture();val p=StudentDeletion.preview(d,wrong.id,h.id)
        assertNull(p.blockedReason);assertTrue(p.exportPosition)
        assertEquals(mapOf("Atividades com notas lançadas" to 1,"Lançamentos de nota pendentes" to 1,"Registros de frequência" to 4,
            "Recomposições com nota" to 1,"Recomposições sem nota" to 1,"Observações desta matrícula" to 1),p.counts)
        assertEquals(mapOf("P" to 1,"F" to 1,"FJ" to 1,"NR" to 1),p.markCounts)
        assertEquals(d,fixture()) // conferir não modifica o conjunto
        val empty=StudentDeletion.preview(d.copy(students=listOf(wrong.copy(notes="")),grades=emptyList(),marks=emptyList(),recompositions=emptyList(),exportRows=emptyList()),wrong.id,h.id)
        assertTrue(empty.counts.isEmpty());assertFalse(empty.exportPosition)
    }
    @Test fun removesOnlySelectedIdAndKeepsSharedActivitiesHoursAndExactOtherRows() {
        val d=fixture();val out=StudentDeletion.without(d,wrong.id)
        assertEquals(d.copy(students=listOf(kept,other),grades=d.grades.filter{it.studentId==other.id},marks=d.marks.filter{it.studentId==other.id},
            recompositions=emptyList(),exportRows=d.exportRows.map{if(it.studentId==wrong.id)StudentDeletion.blank(it)else it}),out)
        assertEquals(d.activities,out.activities);assertEquals(d.lessons,out.lessons)
        assertEquals(d.exportRows.last(),out.exportRows.last());assertEquals(2,out.exportRows.first{it.studentId==other.id}.position)
        ExportLayout.validate(out.classes,out.students,out.exportRows)
    }
    @Test fun nameAndPersonAreNeverDeletionKeys() {
        val d=fixture().let{it.copy(students=it.students.map{if(it.id==kept.id)it.copy(personId=wrong.personId)else it})}
        assertNull(StudentDeletion.preview(d,wrong.id,h.id).blockedReason)
        assertEquals(d.students.filter{it.id!=wrong.id},StudentDeletion.without(d,wrong.id).students)
        try {StudentDeletion.preview(d,wrong.id,g.id);fail("Não pode conferir matrícula de outra turma")}catch(_:IllegalArgumentException){}
    }
    @Test fun protectsRelocationAndItsPriorCorrectionAudit() {
        val d=fixture();val link=Relocation(wrong.id,kept.id,"2026-09-01",corrections=listOf(RelocationCorrection("audit","2026-10-01T12:00:00Z","2026-09-02","2026-09-01","Externo","Corrigido",listOf(RemovedAttendance(d.lessons.first(),d.marks.first())))))
        val p=StudentDeletion.preview(d.copy(relocations=listOf(link)),wrong.id,h.id)
        assertNotNull(p.blockedReason);assertEquals(1,p.counts["Remanejamentos vinculados"]);assertEquals(1,p.counts["Correções administrativas vinculadas"])
        assertNotNull(StudentDeletion.preview(d.copy(relocations=listOf(link)),kept.id,g.id).blockedReason)
    }
    @Test fun preservedHistoryAndCrossClassReferencesBlockOnlyUnsafeDeletion() {
        val d=fixture();val source=wrong.copy(id="historic",classId="historic-class")
        val content=HistoricalContent(h.copy(id=source.classId),listOf(source),emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),listOf(Relocation(source.id,wrong.id,"2026-09-01")))
        assertNotNull(StudentDeletion.preview(d.copy(histories=listOf(PreservedHistory(source.classId,content))),wrong.id,h.id).blockedReason)
        assertNotNull(StudentDeletion.preview(d.copy(grades=d.grades+Grade("activity-of-another-class",wrong.id,1.0)),wrong.id,h.id).blockedReason)
        assertNotNull(StudentDeletion.preview(d.copy(students=d.students.map{if(it.id==wrong.id)it.copy(status="Remanejado")else it}),wrong.id,h.id).blockedReason)
    }
    @Test fun changedValuesWithIdenticalCountsInvalidatePreview() {
        val d=fixture();val old=StudentDeletion.preview(d,wrong.id,h.id)
        val changed=StudentDeletion.preview(d.copy(grades=d.grades.map{if(it.value==0.0)it.copy(value=9.0)else it}),wrong.id,h.id)
        assertEquals(old.counts,changed.counts);assertNotEquals(old,changed)
    }
    @Test fun administrativeTransferWithoutSharedLinksRemainsDeletable() {
        val d=fixture().let{it.copy(students=it.students.map{if(it.id==wrong.id)it.copy(status="Transferido",left="2026-09-30")else it})}
        assertNull(StudentDeletion.preview(d,wrong.id,h.id).blockedReason)
    }
    @Test fun unrelatedPreservedHistoryDoesNotBlockIndependentEnrollmentWithRecords() {
        val d=fixture()
        val historic=kept.copy(id="unrelated-historic",classId="unrelated-class",left="2026-08-31",status="Remanejado")
        val content=HistoricalContent(g.copy(id=historic.classId),listOf(historic),emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),
            listOf(Relocation(historic.id,kept.id,"2026-09-01")))
        val current=d.copy(histories=listOf(PreservedHistory(historic.classId,content)))
        val plan=StudentDeletion.preview(current,wrong.id,h.id)
        assertNull(plan.blockedReason);assertEquals(4,plan.counts["Registros de frequência"])
        assertEquals(current.histories,StudentDeletion.without(current,wrong.id).histories)
    }

}
