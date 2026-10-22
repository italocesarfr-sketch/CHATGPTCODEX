package br.com.diariodeclasse

import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.reports.SchoolReports
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class RelocationConversionTest {
    private val x=SchoolClass("x",name="Turma Fictícia X",subject="História",year=2026)
    private val g=x.copy(id="g",name="Turma Fictícia G")
    private val h=x.copy(id="h",name="Turma Fictícia H")
    private val s=Student("s","person",x.id,"Estudante Fictícia Alfa",1,"2026-01-01","2026-09-24","Remanejado", "Observação preservada")
    private val old=Lesson("old",x.id,"2026-08-20",1,"REALIZADA")
    private val wrong=old.copy(id="wrong",date="2026-09-20")
    private val a=Activity("a",x.id,"Atividade fictícia",date=old.date,term=3,maximum=5.0)
    private val link=Relocation(s.id,null,"2026-09-25","Matutino externo")
    private fun data()=DiarySnapshot(classes=listOf(x,g,h),students=listOf(s),
        terms=listOf(x,g,h).flatMap { c -> listOf(Term(c.id,1,"2026-01-01","2026-03-31"),Term(c.id,2,"2026-04-01","2026-06-30"),Term(c.id,3,"2026-07-01","2026-09-30"),Term(c.id,4,"2026-10-01","2026-12-31")) },
        lessons=listOf(old,wrong),marks=listOf(Attendance(old.id,s.id,"P"),Attendance(wrong.id,s.id,"FJ")),
        activities=listOf(a),grades=listOf(Grade(a.id,s.id,4.0)),recompositions=listOf(Recomposition(s.id,3,5.0,"2026-08-21")),relocations=listOf(link),loaded=true)
    private fun convert(d:DiarySnapshot=data(),day:String="2026-09-17",target:String=g.id,remove:Set<String> =setOf(wrong.id))=RelocationCorrections.preview(d,s.id,day,"Texto ignorado",remove,target)
    private fun apply(p:RelocationCorrectionPreview):DiarySnapshot {assertTrue(p.blockers.toString(),p.canConfirm);return RelocationCorrections.proposed(p,RelocationCorrections.audit(p))}
    private fun fails(block:()->Unit) {try{block();fail("Operação insegura aceita")}catch(_:IllegalArgumentException){}}
    @Test fun conversionCreatesOnlyEnrollmentAndAuditNeverAcademicRows() {
        val d=data();val p=convert(d);assertFalse(p.linkedExisting)
        val after=apply(p);val target=p.target!!
        assertNotEquals(s.id,target.id);assertEquals(s.personId,target.personId);assertEquals("2026-09-17",target.joined)
        assertEquals("Ativo",target.status);assertNull(target.left);assertEquals("Remanejado",after.students.first{it.id==s.id}.status)
        assertEquals("2026-09-16",after.students.first{it.id==s.id}.left)
        assertEquals(d.grades,after.grades);assertEquals(d.recompositions,after.recompositions);assertEquals(d.activities,after.activities)
        assertEquals(listOf(Attendance(old.id,s.id,"P")),after.marks);assertTrue(after.marks.none{it.studentId==target.id})
        val current=after.relocations.single();assertNull(current.externalTarget);assertEquals(target.id,current.targetStudentId)
        val audit=current.corrections.single();assertEquals("CONVERSAO_INTERNA",audit.operation)
        assertEquals(s.id,audit.sourceStudentId);assertEquals(g.id,audit.targetClassId);assertEquals("FJ",audit.removed.single().attendance.mark)
        assertEquals(listOf(s.id,target.id),after.lineage(target).map{it.id});BackupCodec.validate(ClassLifecycle.snapshot(after))
    }
    @Test fun multipleExternalCorrectionsAndPreviouslyRemovedFJRemainExactAuditPrefix() {
        var d=data()
        d=apply(RelocationCorrections.preview(d,s.id,"2026-09-23","Noturno"))
        d=apply(RelocationCorrections.preview(d,s.id,"2026-09-17","Noturno corrigido",setOf(wrong.id)))
        val prior=d.relocations.single().corrections
        val after=apply(convert(d,remove=emptySet()));val audits=after.relocations.single().corrections
        assertEquals(prior,audits.take(2));assertEquals(3,audits.size);assertEquals("FJ",audits[1].removed.single().attendance.mark)
        assertTrue(audits.last().removed.isEmpty());assertEquals("Noturno corrigido",audits.last().previousTarget)
        assertNull(audits.last().externalTarget);assertNotNull(audits.last().targetLabel)
        val encoded=BackupCodec.encode(ClassLifecycle.snapshot(after),27,null)
        assertEquals(11,BackupCodec.decode(encoded).envelope.formatVersion)
        assertEquals(ClassLifecycle.snapshot(after).canonical(),BackupCodec.decode(encoded).snapshot)
    }
    @Test fun existingCompatibleEnrollmentAndItsMarksAndGradesArePreserved() {
        val target=s.copy(id="existing",classId=g.id,number=7,joined="2026-09-17",left=null,status="Ativo",notes="Observação própria")
        val lesson=old.copy(id="dest-lesson",classId=g.id,date="2026-09-18")
        val activity=a.copy(id="dest-activity",classId=g.id,date=lesson.date)
        val d=data().copy(students=listOf(s,target),lessons=data().lessons+lesson,marks=data().marks+Attendance(lesson.id,target.id,"P"),activities=listOf(a,activity),grades=listOf(Grade(a.id,s.id,4.0),Grade(activity.id,target.id,3.0)))
        val p=convert(d);assertTrue(p.linkedExisting);assertEquals(target,p.target)
        val after=apply(p);assertEquals(2,after.students.size);assertEquals(d.grades,after.grades)
        assertEquals(target,after.students.first{it.id==target.id});assertEquals(2,after.marks.size)
        val report=SchoolReports(after,g).student(target,3)
        assertEquals(7.0,report.original!!,0.0);assertEquals(7.0,report.total!!,0.0);assertEquals(5.0,report.recomposition!!.value!!,0.0)
        assertEquals(2,report.frequency!!.present)
    }
    @Test fun archivedIncompatibleSelfAndWrongIdentityDestinationsAreBlocked() {
        fails{convert(target=x.id)}
        fails{convert(data().copy(classArchives=listOf(ClassArchive(g.id))))}
        for(changed in listOf(g.copy(year=2025),g.copy(subject="Química"),g.copy(scale=20.0),g.copy(countJustified=false),g.copy(stage="Fundamental"))) {
            fails{convert(data().copy(classes=listOf(x,changed,h)))}
        }
        fails{convert(data().copy(terms=data().terms.filterNot{it.classId==g.id&&it.number==4}))}
        val homonym=s.copy(id="homonym",personId="another-person",classId=g.id,status="Ativo",left=null,joined="2026-09-17")
        fails{convert(data().copy(students=listOf(s,homonym)))}
    }
    @Test fun duplicatesWrongDatesClosedAndAlreadyLinkedEnrollmentsAreBlocked() {
        val target=s.copy(id="existing",classId=g.id,status="Ativo",left=null,joined="2026-09-17")
        fails{convert(data().copy(students=listOf(s,target,target.copy(id="duplicate",number=2))))}
        for(t in listOf(target.copy(joined="2026-09-18"),target.copy(status="Transferido",left="2026-09-20"))) fails{convert(data().copy(students=listOf(s,t)))}
        val ancestor=s.copy(id="ancestor",classId=h.id,left="2026-09-16")
        fails{convert(data().copy(students=listOf(s,target,ancestor),relocations=listOf(link,Relocation(ancestor.id,target.id,"2026-09-17"))))}
    }
    @Test fun cyclesCannotReturnStudentToEarlierClass() {
        val ancestor=s.copy(id="ancestor",classId=g.id,left="2026-07-31")
        val source=s.copy(joined="2026-08-01")
        val d=data().copy(students=listOf(source,ancestor),relocations=listOf(link,Relocation(ancestor.id,source.id,"2026-08-01")))
        assertFalse(CorrectionDestinations.eligible(d,source).any{it.id==g.id})
        fails{convert(d)}
    }
    @Test fun pendingZeroAndRecompositionNeverGetSilentlyRemoved() {
        for(value in listOf(null,0.0)) {
            val later=a.copy(id="later",date=wrong.date)
            val d=data().copy(activities=listOf(a,later),grades=listOf(Grade(a.id,s.id,4.0),Grade(later.id,s.id,value)),recompositions=listOf(Recomposition(s.id,3,value,wrong.date)))
            val p=convert(d);assertFalse(p.canConfirm);assertTrue(p.records.any{it.value==(value?.toString()?:"Pendente")})
            assertEquals(2,d.grades.size);assertEquals(value,d.recompositions.single().value)
        }
        assertFalse(convert(remove=emptySet()).canConfirm)
    }
    @Test fun earlierPendingRecordsInExistingTargetAndDuplicatePeriodRecompositionAreBlocked() {
        val target=s.copy(id="existing",classId=g.id,joined="2026-09-17",left=null,status="Ativo")
        val lesson=old.copy(id="dest",classId=g.id)
        val activity=a.copy(id="dest-a",classId=g.id)
        val base=data().copy(students=listOf(s,target),lessons=data().lessons+lesson,activities=listOf(a,activity))
        fails{convert(base.copy(marks=base.marks+Attendance(lesson.id,target.id,"NR")))}
        fails{convert(base.copy(grades=base.grades+Grade(activity.id,target.id,null)))}
        fails{convert(base.copy(recompositions=base.recompositions+Recomposition(target.id,4,null,"2026-08-20")))}
        val conflict=convert(base.copy(recompositions=base.recompositions+Recomposition(target.id,3,7.0,"2026-09-18")))
        assertFalse(conflict.canConfirm);assertTrue(conflict.blockers.any{it.contains("duas recomposições")})
    }
    @Test fun oldFormatNineAuditRestoresAndConversionRequiresNewBackupFormat() {
        val old=apply(RelocationCorrections.preview(data(),s.id,"2026-09-23","Noturno"))
        val payload=BackupCodec.json.encodeToString(ClassLifecycle.snapshot(old).canonical())
        fun strip(value:JsonElement):JsonElement=when(value){
            is JsonObject -> JsonObject(value.filterKeys{!value.containsKey("confirmedAt") || it !in setOf("operation","sourceStudentId","targetStudentId","targetClassId","targetLabel")}.mapValues{strip(it.value)})
            is JsonArray -> JsonArray(value.map(::strip));else -> value
        }
        val legacyPayload=strip(BackupCodec.json.parseToJsonElement(payload)).toString()
        val envelope=BackupEnvelope("diario-de-classe-backup",9,"br.com.diariodeclasse",8,BackupCodec.V8_IDENTITY,"2026-10-21T00:00:00Z",26,null,BackupCodec.hash(legacyPayload.toByteArray()),legacyPayload)
        val restored=BackupCodec.decode(BackupCodec.json.encodeToString(envelope).toByteArray()).snapshot
        assertEquals(ClassLifecycle.snapshot(old).canonical(),restored)
        val converted=apply(convert(old))
        val current=BackupCodec.decode(BackupCodec.encode(ClassLifecycle.snapshot(converted),27,null))
        fails{BackupCodec.decode(BackupCodec.json.encodeToString(current.envelope.copy(formatVersion=9)).toByteArray())}
    }
    @Test fun forgedOrIncompleteConversionAuditsAndUnilateralInternalCorrectionsAreRejected() {
        val p=convert();val after=apply(p);val link=after.relocations.single();val audit=link.corrections.single()
        for(bad in listOf(audit.copy(operation="OUTRA"),audit.copy(targetStudentId="missing"),audit.copy(targetClassId=x.id),audit.copy(targetLabel=null),audit.copy(sourceStudentId="other"),audit.copy(externalTarget="Não pode coexistir"))) {
            fails{RelocationRules.validateLinks(after.copy(relocations=listOf(link.copy(corrections=listOf(bad)))))}
        }
        fails{RelocationRules.validateLinks(after.copy(relocations=listOf(link.copy(corrections=listOf(audit.copy(operation="CORRECAO_EXTERNA",externalTarget="Destino"))))))}
        fails{RelocationCorrections.preview(after,s.id,"2026-09-18","Outro destino")}
    }
}
