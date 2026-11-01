package br.com.diariodeclasse

import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.export.*
import br.com.diariodeclasse.sync.academic.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AcademicProtocolTest {
    private val school = SchoolClass("class:legacy", name = "Turma Fictícia", subject = "História", year = 2026)
    private val students = (1..3).map { Student("student:$it", "person:$it", school.id, "Estudante Fictício $it", it, "2026-01-01") }
    private val terms = listOf(Term(school.id,1,"2026-01-01","2026-03-31"),Term(school.id,2,"2026-04-01","2026-06-30"),
        Term(school.id,3,"2026-07-01","2026-09-30"),Term(school.id,4,"2026-10-01","2026-12-31"))
    private val activities = listOf(Activity("activity:1",school.id,"Produção fictícia",type="Produção textual",date="2026-08-10",term=3,maximum=10.0))
    private fun data() = BackupSnapshot(listOf(school),students,terms,
        (1..5).map { Lesson("lesson:$it",school.id,"2026-08-10",it,"REALIZADA") },
        listOf(Attendance("lesson:1",students[0].id,"P"),Attendance("lesson:2",students[0].id,"F"),
            Attendance("lesson:3",students[0].id,"FJ","Atestado médico"),Attendance("lesson:4",students[0].id,"NR")),
        activities,listOf(Grade(activities[0].id,students[0].id,0.0),Grade(activities[0].id,students[1].id,null)),
        listOf(ImportDraft("draft",school.id,"Nome Fictício",null,0,0)),
        recompositions=listOf(Recomposition(students[0].id,3,0.0,"2026-08-11")),
        exportRows=students.mapIndexed { i,s -> ExportRow("student:${s.id}",school.id,s.id,i,false) },
        lessonRecords=listOf(LessonRecord("journal",school.id,"2026-08-10","Ação & expressão","Descrição\nSem reescrita <texto>")),
        timetables=listOf(WeeklyTimetable("revision",2026,"2026-01-01",1,"2026-11-01T00:00:00Z",
            listOf(WeeklySlot("slot",school.id,7,12,school.name,school.subject,school.shift)))))
    private fun rows(value: BackupSnapshot = data()) = AcademicRecords.rows(value)
    private fun delta(before: BackupSnapshot, after: BackupSnapshot): List<AcademicChange> {
        val old=rows(before);return AcademicRecords.delta(old,rows(after),old.associate { it.identity to 1L })
    }
    private fun review(before: BackupSnapshot, local: BackupSnapshot, remote: BackupSnapshot, conflicts: Set<AcademicKey> = emptySet()) =
        AcademicMerge.review(rows(before),rows(local),delta(before,remote),rows(before).associate { it.identity to 1L },conflicts)
    private fun fails(block: () -> Unit) { try { block();fail("Unsafe input accepted") } catch (_: IllegalArgumentException) {} }

    @Test fun roundTripKeepsEveryStoredFieldWithoutSelectionOrDerivedResults() {
        val value=data();val restored=AcademicRecords.snapshot(rows(value.copy(selection=AppSelection(2026,school.id))))
        assertEquals(value.canonical(),restored);assertNull(restored.selection)
        assertEquals(setOf("classes","terms","students","lessons","marks","activities","grades","import_drafts","recompositions","export_rows","lesson_records","weekly_timetables"),rows().map { it.kind }.toSet())
    }
    @Test fun zeroNullNoGradeAndNrNoRowAreNotCollapsed() {
        val restored=AcademicRecords.snapshot(rows())
        assertEquals(0.0,restored.grades.first { it.studentId==students[0].id }.value!!,0.0)
        assertNull(restored.grades.first { it.studentId==students[1].id }.value)
        assertFalse(restored.grades.any { it.studentId==students[2].id })
        assertEquals("NR",restored.marks.first { it.lessonId=="lesson:4" }.mark)
        assertFalse(restored.marks.any { it.lessonId=="lesson:5" })
    }
    @Test fun allSixReasonsAndOldFjWithoutReasonRoundTrip() {
        for (reason in listOf<String?>(null)+JustificationReasons.options) {
            val value=data().copy(marks=listOf(Attendance("lesson:3",students[0].id,"FJ",reason)))
            assertEquals(value.canonical(),AcademicRecords.snapshot(rows(value)))
        }
    }
    @Test fun renameWithStudentsGradesMarksAndJournalCanBeReviewedWithoutTouchingChildren() {
        val before=data();val remote=before.copy(classes=listOf(school.copy(name="Nome corrigido")))
        val result=review(before,before,remote)
        assertTrue(result.conflicts.isEmpty());assertFalse(result.invalidGraph)
        assertEquals(remote.canonical(),result.candidate)
        assertEquals(before.students,result.candidate!!.students)
        assertEquals(before.grades,result.candidate.grades);assertEquals(before.marks,result.candidate.marks)
    }
    @Test fun existingV1ConflictIsNeverSilentlyDiscardedEvenForRename() {
        val before=data();val remote=before.copy(classes=listOf(school.copy(name="Nome remoto")))
        val change=delta(before,remote).single();val result=review(before,before,remote,setOf(change.identity))
        assertNull(result.candidate);assertEquals("PRESERVED_CONFLICT",result.conflicts.single().reason)
        assertEquals(rows(before).first { it.kind=="classes" }.payload,result.conflicts.single().local)
    }
    @Test fun sameFieldChangedOnTwoDevicesRetainsBaseLocalAndRemote() {
        val before=data();val local=before.copy(classes=listOf(school.copy(name="Local")))
        val remote=before.copy(classes=listOf(school.copy(name="Remoto")))
        val result=review(before,local,remote);assertNull(result.candidate)
        val c=result.conflicts.single();assertEquals("CONCURRENT_CHANGE",c.reason)
        assertEquals(listOf("Turma Fictícia","Local","Remoto"),listOf(c.base,c.local,c.remote).map { it!!.getValue("name").jsonPrimitive.content })
    }
    @Test fun independentNameAndClassFieldChangesMergeOnlyWithKnownBase() {
        val before=data();val local=before.copy(classes=listOf(school.copy(name="Local")))
        val remote=before.copy(classes=listOf(school.copy(duration=45)))
        val expected=before.copy(classes=listOf(school.copy(name="Local",duration=45))).canonical()
        assertEquals(expected,review(before,local,remote).candidate)
        assertEquals(expected,review(before,remote,local).candidate)
    }
    @Test fun concurrentSensitiveConfigurationIsNotMergedAsLastWriteWins() {
        val before=data();val local=before.copy(classes=listOf(school.copy(low=4.0)))
        val remote=before.copy(classes=listOf(school.copy(duration=45)))
        assertNull(review(before,local,remote).candidate);assertEquals(1,review(before,local,remote).conflicts.size)
    }
    @Test fun independentGradeRecordsMergeWithoutChangingValuesOrOtherStudents() {
        val before=data();val local=before.copy(grades=before.grades.map { if(it.studentId==students[0].id)it.copy(value=1.0)else it })
        val remote=before.copy(grades=before.grades.map { if(it.studentId==students[1].id)it.copy(value=0.0)else it })
        val result=review(before,local,remote).candidate!!
        assertEquals(listOf(1.0,0.0),result.grades.map { it.value });assertEquals(before.students,result.students)
    }
    @Test fun differentGradesForSameStudentAndActivityAreAConflict() {
        val before=data();val local=before.copy(grades=before.grades.map { it.copy(value=2.0) })
        val remote=before.copy(grades=before.grades.map { it.copy(value=3.0) })
        val result=review(before,local,remote);assertNull(result.candidate);assertEquals(2,result.conflicts.size)
    }
    @Test fun invalidDeletionLeavesEntireCandidateUnapplied() {
        val before=data();val classRow=rows().first { it.kind=="classes" }
        val result=AcademicMerge.review(rows(),rows(),listOf(AcademicChange(classRow.kind,classRow.key,1,null)),rows().associate { it.identity to 1L })
        assertNull(result.candidate);assertTrue(result.invalidGraph);assertEquals(before,data())
    }
    @Test fun tombstoneAndRepeatedDeletionProduceNoDuplicateRow() {
        val before=data();val after=before.copy(marks=before.marks.dropLast(1));val changes=delta(before,after)
        assertNull(changes.single().payload)
        assertEquals(after.canonical(),AcademicMerge.review(rows(before),rows(before),changes,rows(before).associate { it.identity to 1L }).candidate)
        assertEquals(after.canonical(),AcademicMerge.review(rows(before),rows(after),changes,rows(before).associate { it.identity to 1L }).candidate)
    }
    @Test fun newRowConflictingWithUntrackedLocalIdIsNotOverwritten() {
        val before=data().copy(lessonRecords=emptyList());val local=data()
        val remote=data().copy(lessonRecords=data().lessonRecords.map { it.copy(title="Remoto") })
        val result=review(before,local,remote);assertNull(result.candidate);assertEquals(1,result.conflicts.size)
    }
    @Test fun identicalConcurrentChangeConvergesWithoutExtraWrite() {
        val before=data();val after=before.copy(classes=listOf(school.copy(name="Igual")))
        assertEquals(after.canonical(),review(before,after,after).candidate)
        assertTrue(AcademicRecords.delta(rows(after),rows(after),emptyMap()).isEmpty())
    }
    @Test fun changedRowsNeedKnownRevisionAndRestoredTombstoneKeepsItsRevision() {
        val before=data();val after=before.copy(classes=listOf(school.copy(name="Alterada")))
        fails { AcademicRecords.delta(rows(before),rows(after),emptyMap()) }
        val empty=rows(before.copy(lessonRecords=emptyList()));val key=rows().first { it.kind=="lesson_records" }.identity
        assertEquals(7L,AcademicRecords.delta(empty,rows(before),mapOf(key to 7)).single().base)
    }
    @Test fun wireBatchPreservesCompositeTypedKeysAndRejectsDuplicates() {
        val changes=delta(data(),data().copy(marks=emptyList()))
        val batch=AcademicBatch("11111111-1111-4111-8111-111111111111",changes);batch.validate()
        assertEquals(batch,AcademicRecords.json.decodeFromString<AcademicBatch>(AcademicRecords.json.encodeToString(batch)))
        fails { batch.copy(changes=changes+changes.first()).validate() }
        val term=rows().first { it.kind=="terms" };assertFalse(term.key.getValue("number").jsonPrimitive.isString)
        assertTrue(term.key.getValue("classId").jsonPrimitive.isString)
    }
    @Test fun malformedUnknownOrDuplicateRowCannotEnterSnapshot() {
        val all=rows();val first=all.first()
        fails { AcademicRecords.snapshot(all+first) }
        fails { AcademicRecords.snapshot(all.map { if(it==first)it.copy(kind="unknown")else it }) }
        fails { AcademicRecords.snapshot(all.map { if(it==first)it.copy(key=buildJsonObject { put("id","another") })else it }) }
    }
    @Test fun previewCannotUseAnUnknownOrSupersededBaseRevision() {
        val before=data();val remote=before.copy(classes=listOf(school.copy(name="Remota")))
        val known=rows(before).associate { it.identity to 1L };val change=delta(before,remote).single()
        val result=AcademicMerge.review(rows(before),rows(before),listOf(change.copy(base=2)),known)
        assertNull(result.candidate);assertEquals("BASE_REVISION_CHANGED",result.conflicts.single().reason)
        fails { AcademicMerge.review(rows(before),rows(before),listOf(change),emptyMap()) }
        val missing=AcademicMerge.review(emptyList(),rows(before),listOf(change),emptyMap())
        assertNull(missing.candidate);assertEquals("BASE_REVISION_CHANGED",missing.conflicts.single().reason)
    }
    @Test fun storedDraftAndArchiveAreExplicitRowsNotStudentImports() {
        val value=data().copy(classArchives=listOf(ClassArchive(school.id)))
        assertEquals(value.canonical(),AcademicRecords.snapshot(rows(value)))
        assertEquals(3,AcademicRecords.snapshot(rows(value)).students.size)
    }
    @Test fun historiesOfDeletedOriginsAndSuccessiveRelocationsRoundTrip() {
        val raw=javaClass.classLoader!!.getResourceAsStream("history-deletion.json")!!.bufferedReader().use { it.readText() }
        val original=BackupCodec.json.decodeFromString<BackupSnapshot>(raw).diarySnapshot()
        val after=ClassLifecycle.without(ClassLifecycle.without(original,"history-x"),"history-g")
        val value=ClassLifecycle.snapshot(after)
        val restored=AcademicRecords.snapshot(rows(value));assertEquals(value.canonical(),restored)
        assertEquals(2,restored.histories.size);assertFalse(restored.classes.any { it.id=="history-x" })
        File("build/academic-protocol-validation").apply { mkdirs() }.resolve("history-rows.json").writeText(
            AcademicRecords.json.encodeToString(AcademicRecords.delta(emptyList(),rows(value),emptyMap())))
    }
    @Test fun correctionsAndExplicitlyRemovedFjRemainAuditNotLiveFrequency() {
        val source=students[0].copy(left="2026-09-09",status="Remanejado")
        val late=Lesson("wrong",school.id,"2026-09-05",1,"REALIZADA")
        val oldLink=Relocation(source.id,null,"2026-09-10","Externo")
        val d=data().diarySnapshot().copy(students=listOf(source)+students.drop(1),lessons=data().lessons+late,
            marks=data().marks+Attendance(late.id,source.id,"FJ","Outro"),relocations=listOf(oldLink))
        val preview=RelocationCorrections.preview(d,source.id,"2026-09-01","Externo corrigido",setOf(late.id))
        assertTrue(preview.canConfirm)
        val corrected=RelocationCorrections.proposed(preview,RelocationCorrections.audit(preview))
        val restored=AcademicRecords.snapshot(rows(ClassLifecycle.snapshot(corrected)))
        assertEquals("Outro",restored.relocations.single().corrections.single().removed.single().attendance.reason)
        assertFalse(restored.marks.any { it.lessonId==late.id });assertEquals(source.id,restored.students.first().id)
        File("build/academic-protocol-validation").apply { mkdirs() }.resolve("audit-rows.json").writeText(
            AcademicRecords.json.encodeToString(AcademicRecords.delta(emptyList(),rows(restored),emptyMap())))
    }
    @Test fun oldWeekendAndHigherTimetablePeriodsAreNotTruncatedOrRenumbered() {
        val value=AcademicRecords.snapshot(rows()).timetables.single()
        assertEquals(7,value.slots.single().weekday);assertEquals(12,value.slots.single().ordinal)
        assertEquals("revision",value.id)
    }
    @Test fun noChangesToExistingBackupReportsOrSpreadsheetProjection() {
        val before=data();val restored=AcademicRecords.snapshot(rows())
        assertEquals(before.canonical(),BackupCodec.decode(BackupCodec.encode(restored,40,null)).snapshot)
        for (model in ExportModel.entries) {
            assertEquals(ExportProjection.build(before.diarySnapshot(),school,ExportOptions(model,3)),
                ExportProjection.build(restored.diarySnapshot(),school,ExportOptions(model,3)))
        }
    }
    @Test fun publishesOnlyFictitiousRowWireForIndependentPostgresCompatibilityTest() {
        val directory=File("build/academic-protocol-validation").apply { mkdirs() }
        val changes=AcademicRecords.delta(emptyList(),rows(data().copy(classArchives=listOf(ClassArchive(school.id)))),emptyMap())
        File(directory,"room-rows.json").writeText(AcademicRecords.json.encodeToString(changes))
        File(directory,"class-with-history.json").writeText(AcademicRecords.json.encodeToString(rows().first { it.kind=="classes" }))
        assertTrue(changes.any { it.kind=="grades" && it.payload!!["value"]==JsonNull })
    }
}
