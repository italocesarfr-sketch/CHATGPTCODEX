package br.com.diariodeclasse

import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.export.*
import br.com.diariodeclasse.reports.SchoolReports
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ClassificationsTest {
    private val c=SchoolClass(id="classification-class",name="Turma Fictícia",subject="História",year=2026)
    private val s=Student(id="classification-student",classId=c.id,name="Estudante Fictício",number=1,joined="2026-01-01")
    private val activity=Activity(id="text-production",classId=c.id,name="Produção Fictícia",type="Produção textual",date="2026-08-10",term=3,maximum=10.0)
    private val data=DiarySnapshot(classes=listOf(c),students=listOf(s),terms=listOf(Term(c.id,3,"2026-07-01","2026-09-30")),activities=listOf(activity),grades=listOf(Grade(activity.id,s.id,0.0)),
        lessons=(1..4).map{Lesson("classification-$it",c.id,"2026-08-10",it,"REALIZADA")},
        marks=listOf("P","F","FJ","NR").mapIndexed{i,mark->Attendance("classification-${i+1}",s.id,mark)})
    @Test fun choicesHaveExactlyTheRequestedOrderAndNoAdditionalReasons() {
        assertEquals(listOf("Prova","Trabalho","Redação","Produção textual","Atividade prática","Seminário","Estudo dirigido","Outro"),activityTypes)
        assertEquals(listOf("Atestado médico","Acompanhamento médico","Falecimento familiar","Convocação oficial","Atividade escolar ou esportiva","Outro"),JustificationReasons.options)
    }
    @Test fun reasonsAreOptionalAndOnlyAcceptedForJustifiedOccurrences() {
        for(mark in listOf("P","F","FJ","NR")) assertTrue(JustificationReasons.valid(Attendance("l","s",mark)))
        for(reason in JustificationReasons.options) {
            assertTrue(JustificationReasons.valid(Attendance("l","s","FJ",reason)))
            for(mark in listOf("P","F","NR")) assertFalse(JustificationReasons.valid(Attendance("l","s",mark,reason)))
        }
        for(reason in listOf("", "Não autorizado")) assertFalse(JustificationReasons.valid(Attendance("l","s","FJ",reason)))
    }
    @Test fun classifyingActivityAndJustificationDoesNotChangeReportsOrExportColumnsAndValues() {
        val plain=data.copy(activities=listOf(activity.copy(type="Redação")))
        val classified=data.copy(marks=data.marks.map{if(it.mark=="FJ")it.copy(reason="Atestado médico")else it})
        for(countJustified in listOf(true,false)) {
            assertEquals(plain.frequency(s,null,countJustified).percent,classified.frequency(s,null,countJustified).percent,0.0)
            assertEquals(SchoolReports(plain,c).student(s,3).attendance,SchoolReports(classified,c).student(s,3).attendance)
        }
        val row=SchoolReports(classified,c).student(s,3)
        assertEquals("Produção textual",row.activities.single().activity.type);assertEquals(0.0,row.original!!,0.0)
        for(model in ExportModel.entries) {
            val a=ExportProjection.build(plain,c,ExportOptions(model,3));val b=ExportProjection.build(classified,c,ExportOptions(model,3))
            assertEquals(a,b);assertEquals(SpreadsheetWriter.clipboard(a),SpreadsheetWriter.clipboard(b))
        }
    }
    @Test fun backupRoundTripPreservesEveryCategoryAndOptionalReasons() {
        for(reason in listOf<String?>(null)+JustificationReasons.options) {
            val value=ClassLifecycle.snapshot(data.copy(marks=data.marks.map{if(it.mark=="FJ")it.copy(reason=reason)else it}))
            val decoded=BackupCodec.decode(BackupCodec.encode(value,36,null))
            assertEquals(12,decoded.envelope.formatVersion);assertEquals(10,decoded.envelope.databaseVersion)
            assertEquals(value.canonical(),decoded.snapshot)
        }
    }
    @Test fun distributedFormatElevenWithoutReasonRemainsReadableAndIsNeverFilledAutomatically() {
        val current=ClassLifecycle.snapshot(data)
        val objectValue=BackupCodec.json.parseToJsonElement(BackupCodec.json.encodeToString(current)).jsonObject
        val payload=JsonObject(objectValue+ ("marks" to JsonArray(objectValue.getValue("marks").jsonArray.map{JsonObject(it.jsonObject.filterKeys{k->k!="reason"})}))).toString()
        val old=BackupEnvelope("diario-de-classe-backup",11,"br.com.diariodeclasse",9,BackupCodec.V9_IDENTITY,"2026-01-01T00:00:00Z",35,null,BackupCodec.hash(payload.toByteArray()),payload)
        val read=BackupCodec.decode(BackupCodec.json.encodeToString(old).toByteArray()).snapshot
        assertEquals(current.canonical(),read);assertTrue(read.marks.all{it.reason==null})
    }
    @Test fun invalidReasonOrReasonOnNonJustifiedMarkIsRejectedBeforeRestore() {
        for(mark in listOf("P","F","NR","FJ")) {
            val value=ClassLifecycle.snapshot(data.copy(marks=listOf(Attendance("classification-1",s.id,mark,if(mark=="FJ")"Desconhecido"else"Outro"))))
            try {BackupCodec.encode(value,36,null);fail("Motivo inconsistente aceito")}catch(_:BackupFailure){}
        }
    }
    @Test fun correctionSerializationKeepsReasonAndReadsOldAuditWithoutIt() {
        val removed=RemovedAttendance(data.lessons[2],data.marks[2].copy(reason="Convocação oficial"))
        val correction=RelocationCorrection("audit","2026-10-27T00:00:00Z","2026-08-20","2026-08-10","Destino Fictício","Destino Fictício",listOf(removed))
        val converter=RelocationCorrectionConverters();assertEquals(listOf(correction),converter.decodeCorrections(converter.encodeCorrections(listOf(correction))))
        val legacy=converter.encodeCorrections(listOf(correction.copy(removed=listOf(removed.copy(attendance=removed.attendance.copy(reason=null)))))).replace(",\"reason\":null", "")
        assertNull(converter.decodeCorrections(legacy).single().removed.single().attendance.reason)
    }
}
