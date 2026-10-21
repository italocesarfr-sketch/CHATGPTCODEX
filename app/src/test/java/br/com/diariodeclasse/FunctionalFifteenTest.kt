package br.com.diariodeclasse

import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.export.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipInputStream

class FunctionalFifteenTest {
    private val c=SchoolClass("f15",name="Turma Fictícia",subject="História",year=2026)
    private val s=Student("f15-s","f15-person",c.id,"Estudante Fictícia Alfa",1,"2026-01-01","2026-09-09","Remanejado", "Observação preservada")
    private val l=Lesson("f15-l",c.id,"2026-09-05",2,"REALIZADA")
    private val link=Relocation(s.id,null,"2026-09-10","foi pro noturno")
    private fun data()=DiarySnapshot(classes=listOf(c),students=listOf(s),lessons=listOf(l),relocations=listOf(link))
    private fun fails(block:()->Unit){try {block();fail("Operação indevida aceita")}catch(_:IllegalArgumentException){}}
    @Test fun externalCorrectionRequiresExplicitRemovalAndDoesNotReactivate(){
        val d=data().copy(marks=listOf(Attendance(l.id,s.id,"FJ")))
        val p=RelocationCorrections.preview(d,s.id,"2026-09-01",link.externalTarget!!)
        assertFalse(p.canConfirm);assertEquals("FJ",p.records.single().value)
        assertTrue(RelocationCorrections.preview(d,s.id,"2026-09-01",link.externalTarget,setOf(l.id)).canConfirm)
        assertEquals("Remanejado",d.students.single().status);assertEquals("FJ",d.marks.single().mark)
    }
    @Test fun pendingZeroAndRecompositionAreListedAndBlockWithoutErasure(){
        val a=Activity("f15-a",c.id,"Atividade fictícia",date=l.date,term=3,maximum=10.0)
        for(value in listOf(null,0.0,5.0)) {
            val d=data().copy(activities=listOf(a),grades=listOf(Grade(a.id,s.id,value)),recompositions=listOf(Recomposition(s.id,3,value,l.date)))
            val p=RelocationCorrections.preview(d,s.id,"2026-09-01",link.externalTarget!!)
            assertEquals(2,p.records.size);assertFalse(p.canConfirm);assertEquals(2,p.blockers.size)
        }
    }
    @Test fun noRowAndPersistedNRRemainDifferent(){
        val empty=RelocationCorrections.preview(data(),s.id,"2026-09-01","Noturno")
        val nr=RelocationCorrections.preview(data().copy(marks=listOf(Attendance(l.id,s.id,"NR"))),s.id,"2026-09-01","Noturno")
        assertTrue(empty.records.isEmpty());assertEquals("NR",nr.records.single().value);assertTrue(nr.canConfirm)
    }
    @Test fun internalAndInvalidDatesStayProtected(){
        fails {RelocationCorrections.preview(data().copy(relocations=listOf(link.copy(targetStudentId="other"))),s.id,"2026-09-01","Noturno")}
        for(day in listOf("2027-01-01","2026-01-01","invalid")) fails {RelocationCorrections.preview(data(),s.id,day,"Noturno")}
    }
    @Test fun emptyDayAndOnlyNRCanBeDiagnosedWithoutChangingData(){
        val base=data();assertTrue(EmptyDayHours.diagnose(base,c.id,l.date).canRemove)
        val nr=base.copy(marks=listOf(Attendance(l.id,s.id,"NR")))
        val p=EmptyDayHours.diagnose(nr,c.id,l.date)
        assertTrue(p.canRemove);assertEquals(1,p.nrCount);assertEquals(listOf("2º horário: 1 NR"),p.details)
        assertFalse(EmptyDayHours.diagnose(base,c.id,"2026-09-06").canRemove)
        assertEquals(1,nr.marks.size)
    }
    @Test fun everyRelevantMarkBlocksAndActualCountsIdentifyHour(){
        for(mark in listOf("P","F","FJ")) {
            val p=EmptyDayHours.diagnose(data().copy(marks=listOf(Attendance(l.id,s.id,mark))),c.id,l.date)
            assertFalse(p.canRemove);assertEquals(listOf("2º horário: 1 $mark"),p.blockers)
        }
        val l2=l.copy(id="f15-2",ordinal=3)
        val p=EmptyDayHours.diagnose(data().copy(lessons=listOf(l,l2),marks=listOf(Attendance(l.id,s.id,"NR"),Attendance(l2.id,s.id,"F"))),c.id,l.date)
        assertFalse(p.canRemove);assertEquals(1,p.nrCount);assertTrue(p.blockers.single().contains("3º horário"))
    }
    @Test fun notesJournalActivitiesAndRecompositionsIdentifyActualBlockers(){
        for((d,word) in listOf(
            data().copy(lessons=listOf(l.copy(note="Nota"))) to "observação",
            data().copy(lessonRecords=listOf(LessonRecord("r",c.id,l.date,"Título","Texto"))) to "diário",
            data().copy(activities=listOf(Activity("a",c.id,"Prova",date=l.date,term=3,maximum=10.0))) to "atividade",
            data().copy(recompositions=listOf(Recomposition(s.id,3,null,l.date))) to "recomposição")) {
            val p=EmptyDayHours.diagnose(d,c.id,l.date);assertFalse(p.canRemove);assertTrue(p.blockers.joinToString().contains(word))
        }
    }
    @Test fun previousFormatEightWithDeletedOriginStillRestoresWithoutAudit() {
        val source=BackupCodec.json.decodeFromString<BackupSnapshot>(javaClass.classLoader!!.getResourceAsStream("history-deletion.json")!!.bufferedReader().readText()).diarySnapshot()
        val historical=ClassLifecycle.snapshot(ClassLifecycle.without(source,"history-x")).canonical()
        assertTrue(historical.histories.isNotEmpty())
        val payload=BackupCodec.json.encodeToString(historical)
        val envelope=BackupEnvelope("diario-de-classe-backup",8,"br.com.diariodeclasse",7,BackupCodec.V7_IDENTITY,"2026-10-21T00:00:00Z",24,null,BackupCodec.hash(payload.toByteArray()),payload)
        assertEquals(historical,BackupCodec.decode(BackupCodec.json.encodeToString(envelope).toByteArray()).snapshot)
    }
    private fun plans():List<LessonRecord> = listOf(
        LessonRecord("plan-b",c.id,"2026-08-21","Título repetido", "Texto longo: "+"Acentuação, < & > \"aspas\"; çãé. ".repeat(130)+"\nSegunda linha\r\nTerceira linha"),
        LessonRecord("plan-z",c.id,"2026-08-20","Título repetido","=não é fórmula\nTexto exato"),
        LessonRecord("plan-a",c.id,"2026-08-20","Título repetido","Descrição com acentuação.\nParágrafo seguinte."),
        LessonRecord("outside",c.id,"2026-09-01","Fora","Outro período"),
        LessonRecord("another","other","2026-08-20","Outra turma","Não exportar"))
    @Test fun lessonPlansAreIndependentExactChronologicalAndStable(){
        val d=data().copy(lessonRecords=plans());val before=ClassLifecycle.fingerprint(d)
        val t=LessonPlanExportProjection.build(d,c,"2026-08-01","2026-08-31")
        assertEquals(listOf("date","title","description"),t.columns.map{it.key});assertEquals(3,t.records.size)
        assertEquals(listOf("plan-a","plan-z","plan-b").map{id->plans().first{it.id==id}.description},t.records.map{it.cells[2].text})
        assertTrue(t.records.all{it.enrollmentId==null});assertEquals(LocalDate.parse("2026-08-20").toEpochDay()+25569.0,t.records.first().cells.first().number)
        assertEquals(before,ClassLifecycle.fingerprint(d))
    }
    @Test fun emptyPlanExportFailsAndInvalidRangesAreRejected(){
        val t=LessonPlanExportProjection.build(data(),c,"2026-08-01","2026-08-31")
        fails{SpreadsheetWriter.xlsx(t,java.io.ByteArrayOutputStream())}
        fails{LessonPlanExportProjection.build(data(),c,"2026-08-31","2026-08-01")}
    }
    @Test fun exportNativeWorkbookFixturesForIndependentReaders(){
        val folder=File("build/plans-export-validation").apply{mkdirs()}
        val d=data().copy(lessonRecords=plans())
        for((name,start,end) in listOf(Triple("plans","2026-08-01","2026-08-31"),Triple("single","2026-08-21","2026-08-21"))) {
            val t=LessonPlanExportProjection.build(d,c,start,end)
            File(folder,"$name.xlsx").outputStream().use{SpreadsheetWriter.xlsx(t,it)}
            File(folder,"$name.json").writeText(BackupCodec.json.encodeToString(plans().filter{it.classId==c.id&&it.date in start..end}.sortedWith(compareBy({it.date},{it.id}))))
        }
    }
}
