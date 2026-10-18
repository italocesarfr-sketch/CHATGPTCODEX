package br.com.diariodeclasse

import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.export.*
import br.com.diariodeclasse.reports.SchoolReports
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class LessonRecordsTest {
    private val c=SchoolClass(id="c",name="Turma Fictícia",subject="História",year=2026)
    private val note=LessonRecord("r","c","2026-08-10","Título fictício áçã","Descrição livre\nCom aspas \" e tabulação\t.")
    private fun backup()=ClassLifecycle.snapshot(DiarySnapshot(classes=listOf(c),lessonRecords=listOf(note)))
    @Test fun chronologicalFiltersKeepMultipleRecordsAndSeparateClasses() {
        val rows=listOf(note.copy(id="z"),note.copy(id="a"),note.copy(id="b",date="2026-07-01"),note.copy(id="h",classId="h"))
        assertEquals(listOf("a","z"),LessonRecords.list(rows,"c","2026-08-10","2026-08-10").map{it.id})
        assertEquals(listOf("b","a","z"),LessonRecords.list(rows,"c",null,null).map{it.id})
        assertEquals(1,LessonRecords.list(rows,"h",null,null).size)
        assertTrue(LessonRecords.list(rows,"c","2026-09-01",null).isEmpty())
    }
    @Test fun recordDoesNotRequireAttendanceHoursOrPedagogicalFields() {
        LessonRecords.validate(note);LessonRecords.validate(note.copy(title=""));LessonRecords.validate(note.copy(description=""))
        assertThrows(IllegalArgumentException::class.java){LessonRecords.validate(note.copy(title=" ",description="\n"))}
        assertThrows(Exception::class.java){LessonRecords.validate(note.copy(date="31/02/2026"))}
        assertThrows(IllegalArgumentException::class.java){LessonRecords.list(listOf(note),"c","2026-09-01","2026-08-01")}
    }
    @Test fun draftRecreationAndDuplicationPreserveTextAndOriginalIdentity() {
        val original=LessonRecordDraft(note,note);assertFalse(original.dirty)
        val edit=original.copy(value=note.copy(description="Texto alterado"));assertTrue(edit.dirty)
        assertEquals(edit,BackupCodec.json.decodeFromString<LessonRecordDraft>(BackupCodec.json.encodeToString(edit)))
        val duplicate=original.duplicate();assertNull(duplicate.expected);assertTrue(duplicate.dirty)
        assertNotEquals(note.id,duplicate.value.id);assertEquals(note,duplicate.value.copy(id=note.id))
    }
    @Test fun newBackupContainsExactTextAndRejectsMissingTableWrongReferenceAndDuplicateId() {
        val value=backup();val bytes=BackupCodec.encode(value,15,null);val decoded=BackupCodec.decode(bytes)
        assertEquals(8,decoded.envelope.formatVersion);assertEquals(7,decoded.envelope.databaseVersion);assertEquals(value,decoded.snapshot)
        assertThrows(BackupFailure::class.java){BackupCodec.validate(value.copy(lessonRecords=listOf(note,note)))}
        assertThrows(BackupFailure::class.java){BackupCodec.validate(value.copy(lessonRecords=listOf(note.copy(classId="missing"))))}
        val payload=JsonObject(BackupCodec.json.parseToJsonElement(decoded.envelope.payload).jsonObject.filterKeys{it!="lessonRecords"}).toString()
        val envelope=decoded.envelope.copy(payload=payload,payloadSha256=BackupCodec.hash(payload.toByteArray()))
        assertThrows(BackupFailure::class.java){BackupCodec.decode(BackupCodec.json.encodeToString(envelope).toByteArray())}
    }
    @Test fun realV6BackupWithoutNewFieldStillRestoresArchiveAndExportPreferences() {
        val s=Student("s","p",c.id,"Estudante Fictício",1,"2026-01-01")
        val original=backup().copy(students=listOf(s),exportRows=listOf(ExportLayout.studentRow(s,0).copy(manualHidden=true)),classArchives=listOf(ClassArchive(c.id)),lessonRecords=emptyList())
        val payload=JsonObject(BackupCodec.json.encodeToJsonElement(original).jsonObject.filterKeys{it!="lessonRecords"}).toString()
        val old=BackupEnvelope("diario-de-classe-backup",6,"br.com.diariodeclasse",5,BackupCodec.V5_IDENTITY,"2026-10-18T00:00:00Z",14,null,BackupCodec.hash(payload.toByteArray()),payload)
        assertEquals(original,BackupCodec.decode(BackupCodec.json.encodeToString(old).toByteArray()).snapshot)
        val illegal=BackupCodec.json.encodeToString(original.copy(lessonRecords=listOf(note)))
        assertThrows(BackupFailure::class.java){BackupCodec.decode(BackupCodec.json.encodeToString(old.copy(payload=illegal,payloadSha256=BackupCodec.hash(illegal.toByteArray()))).toByteArray())}
    }
    @Test fun readOnlyReportsExportsAndClassDeletionIncludeOnlyTheirOwnData() {
        val old=DiarySnapshot(classes=listOf(c));val changed=old.copy(lessonRecords=listOf(note))
        assertEquals(SchoolReports(old,c).term(1).students,SchoolReports(changed,c).term(1).students)
        for(model in ExportModel.entries)assertEquals(ExportProjection.build(old,c,ExportOptions(model,null)),ExportProjection.build(changed,c,ExportOptions(model,null)))
        assertEquals(1,ClassLifecycle.preview(changed,c.id).counts["Registros de aula"])
        assertTrue(ClassLifecycle.without(changed,c.id).lessonRecords.isEmpty())
    }
}
