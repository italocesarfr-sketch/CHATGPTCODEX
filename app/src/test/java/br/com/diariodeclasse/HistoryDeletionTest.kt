package br.com.diariodeclasse

import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.export.*
import br.com.diariodeclasse.reports.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class HistoryDeletionTest {
    private fun fixture()=BackupCodec.json.decodeFromString<BackupSnapshot>(requireNotNull(javaClass.classLoader?.getResourceAsStream("history-deletion.json")).bufferedReader().use {it.readText()}).diarySnapshot()
    private fun delete(d:DiarySnapshot,id:String):DiarySnapshot {
        assertNull(ClassLifecycle.preview(d,id).blockedReason)
        return ClassLifecycle.without(d,id).also {BackupCodec.validate(ClassLifecycle.snapshot(it));ClassLifecycle.verifyPreservedResults(d,it)}
    }
    @Test fun sourceIsReallyRemovedAndOnlyConnectedEnrollmentsArePreservedWithOriginalIds() {
        val before=fixture();val after=delete(before,"history-x");val history=after.histories.single().content
        assertFalse(after.classes.any {it.id=="history-x"});assertTrue(after.roster("history-x").isEmpty())
        assertEquals(listOf("hx"),history.students.map {it.id});assertEquals(before.students.first {it.id=="hx"},history.students.single())
        assertFalse(after.academic().activities.any {it.id=="exclusive-only"});assertFalse(after.academic().students.any {it.id=="exclusive"})
        assertEquals(before.students.filter {it.classId!="history-x"},after.students)
        assertEquals(2,after.academic().relocations.size)
    }
    @Test fun originalRecompositionDoubleLessonsFjNrAndCancellationKeepSameResults() {
        val before=fixture();val after=delete(before,"history-x");val c=after.classes.first {it.id=="history-h"};val s=after.students.first {it.id=="hh"}
        val row=SchoolReports(after,c).student(s,3)
        assertEquals(4.5,row.original!!,0.0);assertEquals(6.0,row.recomposition!!.value!!,0.0);assertEquals(6.0,row.total!!,0.0)
        assertEquals(8,row.attendance!!.performed);assertEquals(3,row.attendance!!.present);assertEquals(2,row.attendance!!.absent)
        assertEquals(1,row.attendance!!.justified);assertEquals(3,row.attendance!!.effectiveAbsences);assertEquals(50.0,row.attendance!!.percent!!,0.0)
        assertEquals(ActivityScoreState.PENDING,row.activities.first {it.activity.id=="hx-pending"}.state)
        assertEquals(0.0,row.activities.first {it.activity.id=="hx-zero"}.score!!,0.0)
        assertEquals(before.marks.filter {it.studentId=="hx"},after.histories.single().content.marks)
    }
    @Test fun successiveAndIntermediateDeletionHaveIdenticalCanonicalOutcomeWithoutDuplicates() {
        val d=fixture();val a=delete(delete(d,"history-x"),"history-g");val b=delete(delete(d,"history-g"),"history-x")
        assertEquals(ClassLifecycle.snapshot(a).canonical(),ClassLifecycle.snapshot(b).canonical())
        assertEquals(listOf("hx","hg","hh"),a.lineage(a.students.first {it.id=="hh"}).map {it.id})
        assertEquals(5,a.academic().grades.size);assertEquals(2,a.academic().relocations.size)
        assertEquals(a.academic().activities.size,a.academic().activities.map {it.id}.distinct().size)
    }
    @Test fun exportsInAllModelsAndFourPeriodsMatchExactlyAfterBothOriginsAreDeleted() {
        val d=fixture();val after=delete(delete(d,"history-x"),"history-g");val c=d.classes.first {it.id=="history-h"}
        for(model in ExportModel.entries) for(term in listOf(null,1,2,3,4)) {
            val options=ExportOptions(model,term)
            assertEquals(ExportProjection.build(d,c,options),ExportProjection.build(after,c,options))
        }
    }
    @Test fun backupRoundTripIncludesDetachedHistoryAndReadsPreviousFormatSeven() {
        val d=fixture();val after=delete(delete(d,"history-x"),"history-g");val snapshot=ClassLifecycle.snapshot(after).canonical()
        assertEquals(snapshot,BackupCodec.decode(BackupCodec.encode(snapshot,18,null)).snapshot)
        val oldPayload=BackupCodec.json.encodeToString(ClassLifecycle.snapshot(d))
        val old=BackupEnvelope("diario-de-classe-backup",7,"br.com.diariodeclasse",6,BackupCodec.V6_IDENTITY,"2026-10-18T00:00:00Z",17,null,BackupCodec.hash(oldPayload.toByteArray()),oldPayload)
        assertEquals(ClassLifecycle.snapshot(d).canonical(),BackupCodec.decode(BackupCodec.json.encodeToString(old).toByteArray()).snapshot)
    }
    @Test fun deletingFinalConnectedClassReleasesHistoryAndUnrelatedClassRemainsUnchanged() {
        val d=fixture();val after=delete(delete(delete(d,"history-x"),"history-g"),"history-h")
        assertTrue(after.histories.isEmpty());assertTrue(after.relocations.isEmpty());assertEquals(listOf("history-other"),after.classes.map {it.id})
        assertEquals(d.students.filter {it.classId=="history-other"},after.students)
    }
    @Test fun deletingDestinationPreservesOriginHistoryAndDoesNotReactivateItsEnrollment() {
        val d=fixture();val after=delete(d,"history-h")
        assertEquals(d.students.first {it.id=="hg"},after.students.first {it.id=="hg"})
        assertEquals("Remanejado",after.students.first {it.id=="hg"}.status)
        assertEquals(2,after.academic().relocations.size);assertEquals("history-h",after.histories.single().classId)
    }
    @Test fun corruptedOrDuplicateDetachedOriginsAreRejectedInsteadOfMergedSilently() {
        val after=delete(fixture(),"history-x");val s=ClassLifecycle.snapshot(after)
        fun rejected(value:BackupSnapshot) {try {BackupCodec.validate(value);fail("Deveria recusar histórico inválido")}catch(_:BackupFailure){}}
        rejected(s.copy(histories=s.histories+s.histories))
        val h=s.histories.single();rejected(s.copy(histories=listOf(h.copy(content=h.content.copy(students=emptyList())))))
        rejected(s.copy(students=s.students+h.content.students))
        val origin=h.content.school;rejected(s.copy(histories=listOf(h.copy(content=h.content.copy(school=origin.copy(countJustified=false))))))
        rejected(s.copy(histories=listOf(h.copy(content=h.content.copy(school=origin.copy(scale=20.0))))))
        rejected(s.copy(histories=listOf(h.copy(content=h.content.copy(terms=h.content.terms.map {if(it.number==3) it.copy(start="2026-08-01") else it})))))
    }
    @Test fun realSpreadsheetsAfterDeletionUseTheSameNumbersZeroBlanksAndOriginLabels() {
        val after=delete(delete(fixture(),"history-g"),"history-x");val school=after.classes.first {it.id=="history-h"}
        val folder=File("build/history-export-validation");folder.mkdirs()
        val expected=buildJsonArray {
            for(model in ExportModel.entries) for(term in listOf(3,null)) {
                val table=ExportProjection.build(after,school,ExportOptions(model,term));val name="DELETED-${model.name}-${term?:"YEAR"}"
                File(folder,"$name.xlsx").outputStream().use {SpreadsheetWriter.xlsx(table,it)}
                File(folder,"$name.csv").outputStream().use {SpreadsheetWriter.csv(table,it)}
                File(folder,"$name.tsv").writeText(SpreadsheetWriter.clipboard(table))
                File(folder,"$name-notes.tsv").writeText(SpreadsheetWriter.clipboard(table,true))
                File(folder,"$name-all-notes.tsv").writeText(SpreadsheetWriter.clipboard(table,true,true))
                fun matrix(rows:List<List<ExportCell>>)=buildJsonArray {rows.forEach {row->add(buildJsonArray {row.forEach {v->add(v.number?.let(::JsonPrimitive)?:v.text?.takeIf {it.isNotEmpty()}?.let(::JsonPrimitive)?:JsonNull)}})}}
                add(buildJsonObject {put("name",name);put("columns",table.columns.size);put("rows",matrix(table.rows()));put("notes",matrix(table.rows(true)));put("allNotes",matrix(table.rows(true,true)))})
            }
        }
        File(folder,"expected.json").writeText(expected.toString())
    }
}
