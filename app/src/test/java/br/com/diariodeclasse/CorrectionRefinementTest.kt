package br.com.diariodeclasse

import br.com.diariodeclasse.data.*
import org.junit.Assert.*
import org.junit.Test

class CorrectionRefinementTest {
    private val origin = SchoolClass("origin", name="Turma Fictícia X", subject="História", year=2026)
    private val target = origin.copy(id="target", name="Turma Fictícia G")
    private val source = Student("source","person",origin.id,"Estudante Fictícia",1,"2026-01-01","2026-10-24","Remanejado")
    private val link = Relocation(source.id,null,"2026-10-25","Noturno")
    private val lesson = Lesson("lesson",origin.id,"2026-10-20",1,"REALIZADA")
    private fun data() = DiarySnapshot(classes=listOf(origin,target),students=listOf(source),
        terms=listOf(origin,target).flatMap { c -> (1..4).map { Term(c.id,it,"2026-01-01","2026-12-31") } },
        lessons=listOf(lesson),marks=listOf(Attendance(lesson.id,source.id,"FJ")),relocations=listOf(link))
    @Test fun selectionRevalidationUsesSameSnapshotAndDoesNotWrite() {
        val d=data()
        val blocked=RelocationCorrections.preview(d,source.id,"2026-10-17","Noturno")
        assertFalse(blocked.canConfirm)
        assertEquals(setOf(lesson.id),blocked.attendanceBlockers.keys)
        assertTrue(blocked.blockers.single().contains("20/10/2026 • 1º horário • FJ"))
        assertFalse(blocked.blockers.single().contains("2026-10-20"))
        val selected=RelocationCorrections.preview(d,source.id,"2026-10-17","Noturno",setOf(lesson.id))
        assertTrue(selected.canConfirm)
        assertTrue(selected.attendanceBlockers.isEmpty())
        assertEquals(blocked.records,selected.records)
        assertFalse(RelocationCorrections.preview(d,source.id,"2026-10-17","Noturno").canConfirm)
        assertEquals(listOf(Attendance(lesson.id,source.id,"FJ")),d.marks)
        assertEquals(link,d.relocations.single())
    }
    @Test fun eligibleDestinationsExcludeOriginArchivedAndIncompatibleClasses() {
        val d=data()
        val wrongYear=target.copy(id="year",year=2025)
        val wrongSubject=target.copy(id="subject",subject="Química")
        val archived=target.copy(id="archived")
        val all=d.copy(classes=d.classes+listOf(wrongYear,wrongSubject,archived),
            terms=d.terms+listOf(wrongYear,wrongSubject,archived).flatMap { c -> d.terms.filter{it.classId==target.id}.map {it.copy(classId=c.id)} },
            classArchives=listOf(ClassArchive(archived.id)))
        assertEquals(listOf(target),CorrectionDestinations.eligible(all,source))
    }
    @Test fun internalSelectionLinksExistingIdentityWithoutSavingExternalText() {
        val enrolled=source.copy(id="destination",classId=target.id,status="Ativo",left=null,joined="2026-10-25")
        val d=data().copy(students=listOf(source,enrolled))
        val preview=RelocationCorrections.preview(d,source.id,"2026-10-25","Texto não utilizado",targetClassId=target.id)
        assertTrue(preview.canConfirm);assertTrue(preview.linkedExisting);assertEquals(enrolled,preview.target)
        val after=RelocationCorrections.proposed(preview,RelocationCorrections.audit(preview))
        assertNull(after.relocations.single().externalTarget);assertEquals(enrolled.id,after.relocations.single().targetStudentId)
        assertEquals(d.marks,after.marks);assertEquals(d.students,after.students)
        for(id in listOf(origin.id,"missing")) {
            try{RelocationCorrections.preview(d,source.id,"2026-10-25","",targetClassId=id);fail()}catch(_:IllegalArgumentException){}
        }
    }
    @Test fun dateOnlyDestinationOnlyAndCombinedKeepOriginalRowsAndPendingValues() {
        val noMarks=data().copy(marks=emptyList())
        for((date,targetName) in listOf("2026-10-17" to "Noturno","2026-10-25" to "Destino corrigido","2026-10-17" to "Destino corrigido")) {
            val p=RelocationCorrections.preview(noMarks,source.id,date,targetName)
            assertTrue(p.canConfirm);assertEquals(date,p.effectiveDate);assertEquals(targetName,p.externalTarget)
            assertEquals(link,p.link)
        }
        val a=Activity("a",origin.id,"Atividade fictícia",date="2026-10-20",term=4,maximum=10.0)
        for(value in listOf(null,0.0)) {
            val p=RelocationCorrections.preview(noMarks.copy(activities=listOf(a),grades=listOf(Grade(a.id,source.id,value))),source.id,"2026-10-17","Noturno")
            assertFalse(p.canConfirm);assertEquals(value?.toString()?:"Pendente",p.records.single().value)
        }
    }
}
