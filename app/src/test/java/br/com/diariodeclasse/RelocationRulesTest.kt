package br.com.diariodeclasse

import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.reports.*
import br.com.diariodeclasse.backup.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class RelocationRulesTest {
    private val g=SchoolClass(id="g",name="1º G Fictício",subject="História",year=2026,schedule="0,0,0,2,1,0,0")
    private val h=g.copy(id="h",name="1º H Fictício",schedule="0,0,3,0,0,0,0")
    private val a=Student(id="a",personId="p",classId=g.id,name="Estudante Fictício Alfa",number=1,joined="2026-01-01",left="2026-08-31",status="Remanejado")
    private val b=a.copy(id="b",classId=h.id,number=2,joined="2026-09-01",left=null,status="Ativo")
    private val terms=listOf(g,h).flatMap {c->(1..4).map {n->val d=LocalDate.of(c.year,(n-1)*3+1,1);Term(c.id,n,d.toString(),d.plusMonths(3).minusDays(1).toString())}}
    private val old=Activity("old",g.id,"Trabalho Fictício Origem",date="2026-08-10",term=3,maximum=5.0)
    private val next=old.copy(id="next",classId=h.id,name="Prova Fictícia Destino",date="2026-09-10")
    private val link=Relocation(a.id,b.id,b.joined)
    private val base=DiarySnapshot(classes=listOf(g,h),students=listOf(a,b),terms=terms,activities=listOf(old,next),grades=listOf(Grade(old.id,a.id,2.0),Grade(next.id,b.id,3.5)),relocations=listOf(link))
    private fun reports(d:DiarySnapshot=base)=SchoolReports(d,h)
    private fun fails(action:()->Unit) {try {action();fail("Deveria bloquear") }catch(_:IllegalArgumentException){}}
    @Test fun eligiblePointsTravelWithoutCopyingGradesOrDuplicatingMaximum() {
        RelocationRules.validateLinks(base)
        val row=reports().student(b,3)
        assertEquals(5.5,row.original!!,0.0);assertEquals(10.0,row.grades.maximum,0.0);assertFalse(row.grades.partial)
        assertEquals(listOf(a.id,b.id),row.activities.map {it.enrollmentId})
        assertEquals(2,base.grades.size);assertEquals(EnrollmentCoverage.FULL,row.enrollment)
        assertEquals(2.0,SchoolReports(base,g,true).student(a,3).original!!,0.0)
    }
    @Test fun zeroPendingAndForeignActivitiesNeverBecomeArtificialScores() {
        val before=next.copy(id="before",date="2026-08-01")
        val after=old.copy(id="after",date="2026-09-20")
        val d=base.copy(activities=base.activities+listOf(before,after),grades=listOf(Grade(old.id,a.id,0.0)))
        val row=reports(d).student(b,3)
        assertEquals(0.0,row.original!!,0.0);assertEquals(1,row.grades.pending)
        assertEquals(ActivityScoreState.RECORDED,row.activities.first {it.activity.id==old.id}.state)
        assertEquals(ActivityScoreState.PENDING,row.activities.first {it.activity.id==next.id}.state)
        assertEquals(ActivityScoreState.NOT_APPLICABLE,row.activities.first {it.activity.id==before.id}.state)
        assertEquals(10.0,row.grades.maximum,0.0)
        assertNull(reports(d.copy(grades=emptyList())).student(b,3).total)
    }
    @Test fun differentGridsDoubleLessonsJustificationsAndCancellationsKeepOriginalIds() {
        val lessons=(1..4).map {Lesson("g$it",g.id,"2026-08-10",it,"REALIZADA")}+
            (1..3).map {Lesson("h$it",h.id,"2026-09-10",it,"REALIZADA")}+Lesson("cancel",g.id,"2026-08-11",1,"CANCELADA")
        val marks=listOf(Attendance("g1",a.id,"P"),Attendance("g2",a.id,"F"),Attendance("g3",a.id,"FJ"),Attendance("g4",a.id,"NR"),Attendance("h1",b.id,"P"),Attendance("h2",b.id,"F"),Attendance("cancel",a.id,"F"))
        for(flag in listOf(true,false)) {
            val school=h.copy(countJustified=flag)
            val d=base.copy(classes=listOf(g.copy(countJustified=flag),school),lessons=lessons,marks=marks)
            RelocationRules.validateLinks(d)
            val projection=SchoolReports(d,school);val f=projection.student(b,3).attendance!!
            assertEquals(7,f.performed);assertEquals(5,f.valid);assertEquals(2,f.present);assertEquals(2,f.absent);assertEquals(1,f.justified);assertEquals(2,f.unregistered)
            assertEquals(if(flag)3 else 2,f.effectiveAbsences);assertEquals(if(flag)40.0 else 60.0,f.percent!!,0.0)
            val history=projection.attendanceHistory(b);assertEquals(8,history.size);assertEquals(g.id,history.first().school.id)
            assertEquals(2,projection.year(b).frequency.absent)
        }
    }
    @Test fun sourceIsHiddenByDefaultAndHistoryRemainsReadable() {
        assertTrue(SchoolReports(base,g).term(3).students.isEmpty())
        assertEquals(a.id,SchoolReports(base,g,true).term(3).students.single().student.id)
        assertEquals(b.id,reports().term(3).students.single().student.id)
        assertEquals(1,base.roster(g.id).size);assertTrue(base.currentRoster(g.id).isEmpty())
        assertEquals(listOf(a,b),base.lineage(b))
    }
    @Test fun oneRecompositionIsSubstitutiveAndConflictingRecordsAreBlocked() {
        for(value in listOf(null,0.0,3.0,5.5,8.0)) {
            val r=Recomposition(a.id,3,value,"2026-08-20")
            val d=base.copy(recompositions=listOf(r));RelocationRules.validateLinks(d)
            val row=reports(d).student(b,3)
            assertEquals(r,row.recomposition);assertEquals(if(value==null)5.5 else maxOf(5.5,value),row.total!!,0.0)
            assertEquals(2,d.grades.size)
            fails {RelocationRules.validateLinks(d.copy(recompositions=listOf(r,r.copy(studentId=b.id,value=null))))}
        }
    }
    @Test fun periodBoundariesKeepFourResultsAndOriginalRecompositionOrigin() {
        val ended=a.copy(left="2026-09-30");val target=b.copy(joined="2026-10-01")
        val q4=next.copy(term=4,date="2026-11-10")
        val d=base.copy(students=listOf(ended,target),activities=listOf(old,q4),relocations=listOf(link.copy(effectiveDate=target.joined)),recompositions=listOf(Recomposition(a.id,3,7.0,"2026-09-20")))
        RelocationRules.validateLinks(d)
        val rows=reports(d).year(target).terms
        assertEquals(listOf(null,null,7.0,3.5),rows.map {it.total})
        assertEquals(2.0,rows[2].original!!,0.0);assertNull(rows[3].recomposition)
    }
    @Test fun incompatibleCriteriaCyclesDuplicateLinksAndIdentityChangesAreRejected() {
        for(school in listOf(h.copy(year=2025),h.copy(scale=20.0),h.copy(countJustified=false),h.copy(subject="Matemática"),h.copy(stage="Fundamental"))) {
            fails {RelocationRules.validateLinks(base.copy(classes=listOf(g,school)))}
        }
        fails {RelocationRules.validateLinks(base.copy(terms=terms.map {if(it.classId==h.id&&it.number==3)it.copy(start="2026-07-02")else it}))}
        fails {RelocationRules.validateLinks(base.copy(relocations=listOf(link,link)))}
        fails {RelocationRules.validateLinks(base.copy(students=listOf(a,b.copy(personId="outra-pessoa"))))}
        fails {RelocationRules.validateLinks(base.copy(students=listOf(a,b.copy(joined="2026-08-20"))))}
        fails {base.copy(relocations=listOf(link,Relocation(b.id,a.id,"2026-10-01"))).lineage(b)}
    }
    @Test fun backupFormatFourPreservesLinksAndVersionThreeHasNoInventedLinks() {
        fun snapshot(d:DiarySnapshot)=BackupSnapshot(d.classes,d.students,d.terms,d.lessons,d.marks,d.activities,d.grades,d.drafts,recompositions=d.recompositions,relocations=d.relocations)
        val value=snapshot(base).canonical();val decoded=BackupCodec.decode(BackupCodec.encode(value,11,null))
        assertEquals(4,decoded.envelope.formatVersion);assertEquals(3,decoded.envelope.databaseVersion);assertEquals(value,decoded.snapshot)
        val old=value.copy(relocations=emptyList())
        val payload=JsonObject(BackupCodec.json.parseToJsonElement(BackupCodec.json.encodeToString(old)).jsonObject.filterKeys {it!="relocations"}).toString()
        val envelope=BackupEnvelope("diario-de-classe-backup",3,"br.com.diariodeclasse",2,BackupCodec.V2_IDENTITY,"2026-10-17T00:00:00Z",10,null,BackupCodec.hash(payload.toByteArray()),payload)
        assertEquals(old,BackupCodec.decode(BackupCodec.json.encodeToString(envelope).toByteArray()).snapshot)
        val missing=JsonObject(BackupCodec.json.parseToJsonElement(decoded.envelope.payload).jsonObject.filterKeys {it!="relocations"}).toString()
        fails {BackupCodec.decode(BackupCodec.json.encodeToString(decoded.envelope.copy(payload=missing,payloadSha256=BackupCodec.hash(missing.toByteArray()))).toByteArray())}
        fails {BackupCodec.validate(value.copy(relocations=listOf(link.copy(targetStudentId="inexistente"))))}
    }
    @Test fun distinctActivitiesCanExceedAvailableScaleWithoutNormalizationOrAdditiveRecomposition() {
        val data=base.copy(activities=listOf(old.copy(maximum=10.0),next.copy(maximum=10.0)),grades=listOf(Grade(old.id,a.id,8.0),Grade(next.id,b.id,7.0)),recompositions=listOf(Recomposition(a.id,3,9.0,"2026-08-20")))
        val row=reports(data).student(b,3)
        assertEquals(20.0,row.grades.maximum,0.0);assertEquals(10.0,row.original!!,0.0);assertEquals(10.0,row.total!!,0.0)
        assertEquals(2,row.activities.distinctBy {it.activity.id}.size)
    }
    @Test fun externalDestinationPreservesSourceWithoutCreatingClassOrEnrollment() {
        val link=Relocation(a.id,null,"2026-09-01","Outra Turma Fictícia Externa")
        val d=base.copy(students=listOf(a),classes=listOf(g),terms=terms.filter {it.classId==g.id},activities=listOf(old),grades=base.grades.take(1),relocations=listOf(link))
        RelocationRules.validateLinks(d)
        assertEquals(listOf(a),d.lineage(a));assertEquals(1,d.students.size);assertEquals(1,d.classes.size)
        assertEquals(2.0,SchoolReports(d,g,true).student(a,3).original!!,0.0)
    }
}
