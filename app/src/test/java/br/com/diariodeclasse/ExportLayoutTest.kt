package br.com.diariodeclasse

import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.reports.SchoolReports
import br.com.diariodeclasse.ui.ExportEditSession
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ExportLayoutTest {
    private val c=SchoolClass(id="export-c",name="Turma Fictícia Exportação",subject="História",year=2026)
    private val h=c.copy(id="export-h",name="Destino Fictício Exportação")
    private val a=Student("export-a","person-a",c.id,"Estudante Fictício Homônimo",10,"2026-01-01")
    private val b=a.copy(id="export-b",personId="person-b",number=20)
    private val base=DiarySnapshot(classes=listOf(c,h),students=listOf(a,b))
    private fun fails(action:()->Unit) {try {action();fail("Deveria rejeitar")}catch(_:IllegalArgumentException){}}
    private fun backup(d:DiarySnapshot)=BackupSnapshot(d.classes,d.students,d.terms,d.lessons,d.marks,d.activities,d.grades,d.drafts,recompositions=d.recompositions,relocations=d.relocations,exportRows=d.exportRows)
    @Test fun newStudentsAreIncludedAndHomonymsUseEnrollmentIds() {
        val rows=ExportLayout.rows(base,c.id);assertEquals(listOf(a.id,b.id),rows.map{it.studentId})
        val draft=ExportEditSession.start(base,c.id,"HIDE").hide(a.id,true)
        val slots=ExportLayout.project(base,draft.rows)
        assertFalse(slots[0].included);assertTrue(slots[1].included)
        assertEquals(a.name,b.name);assertEquals(2,rows.map{it.id}.distinct().size)
    }
    @Test fun multipleSelectionsCanBeReversedAndSessionSurvivesSerialization() {
        val original=ExportEditSession.start(base,c.id,"HIDE")
        val selected=original.hide(a.id,true).hide(b.id,true)
        assertEquals(2,selected.rows.count{it.manualHidden});assertTrue(selected.dirty)
        val restored=ExportEditSession.decode(selected.encode());assertEquals(selected,restored)
        assertEquals(original,restored.hide(a.id,false).hide(b.id,false));assertFalse(original.dirty)
        assertTrue(base.exportRows.isEmpty()) // Cancelar descarta o rascunho, sem escrever preferências.
    }
    @Test fun rearrangementAndBlankLinesKeepOfficialNumbersUntouched() {
        val original=ExportEditSession.start(base,c.id,"ORDER")
        val moved=original.move("student:${b.id}",-1).addBlank("student:${b.id}").addBlank()
        assertEquals(listOf(b.id,null,a.id,null),moved.rows.map{it.studentId})
        assertEquals(listOf(0,1,2,3),moved.rows.map{it.position})
        assertEquals(listOf(10,20),base.roster(c.id).map{it.number})
        val blank=moved.rows[1]
        assertEquals(listOf(b.id,a.id,null),moved.removeBlank(blank.id).rows.map{it.studentId})
        fails {moved.removeBlank("student:${a.id}")}
    }
    @Test fun renamedAndRenumberedStudentsKeepSavedPositionsAndNewEnrollmentsAppend() {
        val saved=ExportEditSession.start(base,c.id,"ORDER").move("student:${b.id}",-1).addBlank("student:${b.id}").rows
        val new=a.copy(id="new",personId="new-person",name="AAAA Estudante Fictício",number=1)
        val changed=base.copy(students=listOf(a.copy(name="ZZZ Estudante Fictício",number=50),b.copy(number=40),new),exportRows=saved)
        assertEquals(saved,ExportLayout.rows(changed,c.id).take(saved.size))
        assertEquals(listOf(b.id,null,a.id,new.id),ExportLayout.rows(changed,c.id).map{it.studentId})
    }
    @Test fun movementExcludesOnlySourceAutomaticallyAndNeverInheritsManualHiding() {
        val source=a.copy(status="Remanejado",left="2026-08-31")
        val destination=a.copy(id="destination",classId=h.id,joined="2026-09-01")
        val d=base.copy(students=listOf(source,b,destination),relocations=listOf(Relocation(source.id,destination.id,"2026-09-01")),exportRows=listOf(ExportLayout.studentRow(source,0).copy(manualHidden=true)))
        val origin=ExportLayout.project(d,ExportLayout.rows(d,c.id)).first()
        assertTrue(origin.automaticallyExcluded);assertTrue(origin.row.manualHidden);assertFalse(origin.included)
        val target=ExportLayout.project(d,ExportLayout.rows(d,h.id)).single()
        assertTrue(target.included);assertFalse(target.row.manualHidden);assertFalse(target.automaticallyExcluded)
        val unhidden=origin.row.copy(manualHidden=false)
        assertFalse(ExportLayout.project(d,listOf(unhidden)).single().included)
    }
    @Test fun previewKeepsOnlyExplicitBlankLinesAndExplainsExcludedRowsSeparately() {
        val draft=ExportEditSession.start(base,c.id,"ORDER").addBlank("student:${a.id}").hide(a.id,true)
        val slots=ExportLayout.project(base,draft.rows)
        assertEquals(listOf(null,b.id),slots.filter{it.included}.map{it.row.studentId})
        assertEquals(listOf(a.id),slots.filterNot{it.included}.map{it.student!!.id})
        assertTrue(slots.filter{it.included}.first().blank)
    }
    @Test fun foreignEnrollmentsDuplicateIdsDuplicatePositionsAndHiddenBlankAreRejected() {
        val rows=ExportLayout.rows(base,c.id)
        fails {ExportLayout.validate(base.classes,base.students,rows+rows.first())}
        fails {ExportLayout.validate(base.classes,base.students,listOf(rows[0],rows[1].copy(position=0)))}
        fails {ExportLayout.validate(base.classes,base.students,listOf(rows[0].copy(classId=h.id)))}
        fails {ExportLayout.validate(base.classes,base.students,listOf(ExportLayout.blank(c.id).copy(manualHidden=true)))}
        fails {ExportLayout.validate(base.classes,base.students,listOf(rows[0].copy(studentId="missing")))}
    }
    @Test fun manualPreferenceDoesNotChangeSchoolRosterGradesOrFrequencyProjection() {
        val term=Term(c.id,3,"2026-07-01","2026-09-30")
        val activity=Activity("activity",c.id,"Atividade Fictícia",date="2026-08-10",term=3,maximum=10.0)
        val d=base.copy(terms=listOf(term),activities=listOf(activity),grades=listOf(Grade(activity.id,a.id,0.0)),
            lessons=listOf(Lesson("lesson",c.id,"2026-08-10",1,"REALIZADA")),marks=listOf(Attendance("lesson",a.id,"F")),recompositions=listOf(Recomposition(a.id,3,5.0,"2026-08-20")))
        val changed=d.copy(exportRows=ExportEditSession.start(d,c.id,"HIDE").hide(a.id,true).rows)
        val before=SchoolReports(d,c).student(a,3);val after=SchoolReports(changed,c).student(a,3)
        assertEquals(d.currentRoster(c.id),changed.currentRoster(c.id));assertEquals(before.original,after.original)
        assertEquals(5.0,after.total!!,0.0);assertEquals(before.attendance,after.attendance)
        assertEquals(0.0,after.original!!,0.0)
    }
    @Test fun completeBackupRoundTripIncludesConfigurationAndOldFormatFourDefaultsToEmpty() {
        val rows=ExportEditSession.start(base,c.id,"ORDER").hide(a.id,true).addBlank("student:${a.id}").rows
        val original=backup(base.copy(exportRows=rows)).canonical()
        val decoded=BackupCodec.decode(BackupCodec.encode(original,13,null))
        assertEquals(12,decoded.envelope.formatVersion);assertEquals(10,decoded.envelope.databaseVersion);assertEquals(original,decoded.snapshot)
        val old=backup(base).canonical()
        val payload=JsonObject(BackupCodec.json.parseToJsonElement(BackupCodec.json.encodeToString(old)).jsonObject.filterKeys{it!="exportRows"}).toString()
        val envelope=BackupEnvelope("diario-de-classe-backup",4,"br.com.diariodeclasse",3,BackupCodec.V3_IDENTITY,"2026-10-17T00:00:00Z",12,null,BackupCodec.hash(payload.toByteArray()),payload)
        assertEquals(old,BackupCodec.decode(BackupCodec.json.encodeToString(envelope).toByteArray()).snapshot)
        val missing=decoded.envelope.copy(payload=payload,payloadSha256=BackupCodec.hash(payload.toByteArray()))
        fails {BackupCodec.decode(BackupCodec.json.encodeToString(missing).toByteArray())}
    }
    @Test fun corruptOrCrossClassExportPreferencesCannotBeRestored() {
        val value=backup(base.copy(exportRows=listOf(ExportLayout.studentRow(a,0))))
        fails {BackupCodec.validate(value.copy(exportRows=listOf(value.exportRows.single().copy(classId=h.id))))}
        fails {BackupCodec.validate(value.copy(exportRows=value.exportRows+value.exportRows))}
    }
}
