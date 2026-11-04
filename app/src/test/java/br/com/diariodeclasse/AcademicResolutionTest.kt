package br.com.diariodeclasse

import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.sync.academic.*
import kotlinx.serialization.json.*
import org.junit.Test
import org.junit.Assert.*

class AcademicResolutionTest {
    private val fixture get()=BackupCodec.json.decodeFromString<BackupSnapshot>(java.io.File("src/androidTest/assets/phase-d.json").readText()).canonical()
    private fun rows(s:BackupSnapshot)=AcademicRecords.rows(s)
    private fun known(s:BackupSnapshot)=rows(s).associate{it.identity to 1L}
    private fun latest(base:BackupSnapshot,remote:BackupSnapshot):Map<AcademicKey,Long> {
        val before=rows(base).associateBy{it.identity};val after=rows(remote).associateBy{it.identity}
        return (before.keys+after.keys).associateWith{if(before[it]==after[it])1L else 2L}
    }
    @Test fun zeroPendingAndPositiveRemainExplicitVariantsUntilAChoice() {
        val base=fixture;val grade=base.grades.first();val local=base.copy(grades=base.grades.map{if(it==grade)it.copy(value=0.0)else it})
        val remote=base.copy(grades=base.grades.map{if(it==grade)it.copy(value=null)else it})
        val k=known(base);val revisions=latest(base,remote)
        // Ensure a true concurrent edit even when the fixture's first value was already zero.
        val changed=local.copy(grades=local.grades.map{if(it.activityId==grade.activityId&&it.studentId==grade.studentId)it.copy(value=7.0)else it})
        val keep=AcademicResolution.propose(rows(base),rows(changed),rows(remote),k,revisions,false,AcademicChoice.KEEP_LOCAL)
        val use=AcademicResolution.propose(rows(base),rows(changed),rows(remote),k,revisions,false,AcademicChoice.KEEP_REMOTE)
        assertEquals(7.0,keep.candidate.grades.single{it.activityId==grade.activityId&&it.studentId==grade.studentId}.value!!,0.0)
        assertNull(use.candidate.grades.single{it.activityId==grade.activityId&&it.studentId==grade.studentId}.value)
        assertEquals(2,keep.changes.single().base);assertTrue(use.changes.isEmpty());assertEquals(base.marks,keep.candidate.marks)
    }
    @Test fun independentLocalAndRemoteEditsRemainTogetherWithoutInventedConflict() {
        val base=fixture;val c=base.classes.first();val journal=base.lessonRecords.first()
        val local=base.copy(classes=base.classes.map{if(it.id==c.id)it.copy(name="Local")else it})
        val remote=base.copy(lessonRecords=base.lessonRecords.map{if(it.id==journal.id)it.copy(description="Remota")else it})
        val p=AcademicResolution.propose(rows(base),rows(local),rows(remote),known(base),latest(base,remote),false,AcademicChoice.KEEP_REMOTE)
        assertTrue(p.evidence.isEmpty());assertEquals("Local",p.candidate.classes.single{it.id==c.id}.name)
        assertEquals("Remota",p.candidate.lessonRecords.single{it.id==journal.id}.description);assertEquals(1,p.changes.size)
    }
    @Test fun restoringOldBackupNeverInterpretsMissingRemoteRowsAsDeletion() {
        val base=fixture;val extra=base.students.single{it.id=="ds2"}.copy(id="remote-only",personId="remote-person",number=40)
        val remote=base.copy(students=base.students+extra)
        for(choice in AcademicChoice.entries) {
            val p=AcademicResolution.propose(rows(base),rows(base),rows(remote),known(base),latest(base,remote),true,choice)
            assertTrue(p.candidate.students.contains(extra));assertTrue(p.changes.isEmpty())
        }
    }
    @Test fun recordsExclusiveToBackupKeepTheirOriginalPersonAndEnrollmentIds() {
        val base=fixture;val extra=base.students.single{it.id=="ds2"}.copy(id="backup-only",personId="backup-person",number=40)
        val local=base.copy(students=base.students+extra)
        for(choice in AcademicChoice.entries) {
            val p=AcademicResolution.propose(rows(base),rows(local),rows(base),known(base),known(base),true,choice)
            assertTrue(p.candidate.students.contains(extra));assertEquals(0,p.changes.single().base);assertEquals("backup-only",p.changes.single().key["id"]!!.jsonPrimitive.content)
        }
    }
    @Test fun aKnownRemoteTombstoneIsNotConfusedWithAnUnknownLocalOnlyDraft() {
        val base=fixture;val remote=base.copy(drafts=emptyList());val revisions=latest(base,remote)
        val p=AcademicResolution.propose(rows(base),rows(base),rows(remote),known(base),revisions,true,AcademicChoice.KEEP_REMOTE)
        assertTrue(p.candidate.drafts.isEmpty());assertTrue(p.changes.isEmpty())
        val local=AcademicResolution.propose(rows(base),rows(base),rows(remote),known(base),revisions,true,AcademicChoice.KEEP_LOCAL)
        assertEquals(base.drafts,local.candidate.drafts);assertEquals(2,local.changes.single().base)
    }
    @Test fun choosingLocalCannotErasePreviouslyConfirmedMovementAudit() {
        val base=fixture;val bad=base.copy(relocations=base.relocations.map{it.copy(corrections=emptyList())})
        try{AcademicResolution.propose(rows(base),rows(bad),rows(base),known(base),known(base),false,AcademicChoice.KEEP_LOCAL);fail()}catch(_:IllegalArgumentException){}
    }
}
