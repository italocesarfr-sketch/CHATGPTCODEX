package br.com.diariodeclasse

import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.sync.academic.*
import kotlinx.serialization.json.*
import org.junit.Test
import org.junit.Assert.*

class PhaseDGuardTest {
    private val c=SchoolClass("c",name="Fictícia",subject="História",year=2026)
    private val s=Student("s","p",c.id,"Fictício",1,"2026-01-01")
    private val empty=BackupSnapshot(emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList())
    private fun assertBlocked(block:()->Unit){try{block();fail("Must reject incomplete/changed history")}catch(_:IllegalArgumentException){}}
    private fun row(kind:AcademicKind,payload:JsonObject)=AcademicChange(kind.wire,kind.key(payload).key,0,payload)
    private fun slot()=WeeklySlot("slot",c.id,6,12,c.name,c.subject,c.shift)
    private fun revision()=WeeklyTimetable("t",2026,"2026-01-01",1,"2026-10-10T00:00:00Z",listOf(slot()))
    @Test fun completeFictitiousFixtureMatchesRoomSerializersAndAcademicRules() {
        val file=java.io.File("src/androidTest/assets/phase-d.json")
        val data=BackupCodec.json.decodeFromString<BackupSnapshot>(file.readText())
        BackupCodec.validate(data)
        val rows=AcademicRecords.rows(data)
        assertEquals(15,rows.map{it.kind}.toSet().size)
        rows.forEach{r->PhaseBGuard.payload(AcademicChange(r.kind,r.key,0,r.payload))}
        PhaseDGuard.validate(empty,data)
        assertEquals(data.canonical(),AcademicRecords.snapshot(rows))
    }
    @Test fun deletingSourcePreservesEntireLineageResultsAndProducesAtomicHistoryDelta() {
        val before=BackupCodec.json.decodeFromString<BackupSnapshot>(java.io.File("src/androidTest/assets/phase-d.json").readText()).canonical()
        val after=ClassLifecycle.snapshot(PreservedHistories.afterDeletion(before.diarySnapshot(),"d-source")).canonical()
        BackupCodec.validate(after);PhaseDGuard.validate(before,after)
        ClassLifecycle.verifyPreservedResults(before.diarySnapshot(),after.diarySnapshot())
        assertEquals(setOf("d-past","d-source"),after.histories.map{it.classId}.toSet())
        assertEquals(1,after.students.size)
        assertEquals(2,after.diarySnapshot().academic().relocations.single{it.sourceStudentId=="ds1"}.corrections.size)
        val base=AcademicRecords.rows(before);val changes=AcademicRecords.delta(base,AcademicRecords.rows(after),base.associate{it.identity to 1L})
        assertTrue(changes.size<=256);AcademicBatch("99999999-9999-4999-8999-999999999999",changes).validate()
        val folder=java.io.File("build/academic-phase-d-validation").apply{mkdirs()}
        java.io.File(folder,"source-deletion.json").writeText(AcademicRecords.json.encodeToString(kotlinx.serialization.builtins.ListSerializer(AcademicChange.serializer()),changes))
    }
    @Test fun historicalUnmarkedLessonStillBelongsToRetainedEnrollmentPeriod() {
        val data=BackupCodec.json.decodeFromString<BackupSnapshot>(java.io.File("src/androidTest/assets/phase-d.json").readText()).canonical()
        val h=data.histories.single();val lesson=Lesson("empty-historical","d-past","2026-04-11",1,"REALIZADA")
        val before=data.copy(histories=listOf(h.copy(content=h.content.copy(lessons=h.content.lessons+lesson))))
        BackupCodec.validate(before)
        assertBlocked{PhaseDGuard.immutable(before,data)}
    }
    @Test fun allFifteenCompositeIdentitiesAreStable() {
        assertEquals(15,phaseDKinds.size)
        for((kind,field) in listOf("relocations" to "sourceStudentId","class_archives" to "classId","preserved_histories" to "classId","weekly_timetables" to "id","lesson_records" to "id","export_rows" to "id","import_drafts" to "id")) {
            assertEquals(buildJsonObject{put(field,"existing-id")},AcademicState(kind,"existing-id",owner="fixture").identity().key)
        }
    }
    @Test fun nestedSlotsRequireIdsInsteadOfKotlinRandomDefaults() {
        val p=AcademicRecords.json.encodeToJsonElement(WeeklyTimetable.serializer(),revision()).jsonObject
        PhaseBGuard.payload(row(AcademicKind.TIMETABLES,p))
        val bad=JsonObject(p+("slots" to JsonArray(listOf(JsonObject(p["slots"]!!.jsonArray.single().jsonObject-"id")))))
        assertBlocked{PhaseBGuard.payload(row(AcademicKind.TIMETABLES,bad))}
        val wrong=JsonObject(p+("slots" to JsonArray(listOf(JsonObject(p["slots"]!!.jsonArray.single().jsonObject+("weekday" to JsonPrimitive("6")))))))
        assertBlocked{PhaseBGuard.payload(row(AcademicKind.TIMETABLES,wrong))}
    }
    @Test fun nestedHistoryRequiresEveryPersistedField() {
        val history=PreservedHistory(c.id,HistoricalContent(c,listOf(s),emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList()))
        val p=AcademicRecords.json.encodeToJsonElement(PreservedHistory.serializer(),history).jsonObject
        PhaseBGuard.payload(row(AcademicKind.HISTORIES,p))
        val bad=JsonObject(p+("content" to JsonObject(p["content"]!!.jsonObject-"recompositions")))
        assertBlocked{PhaseBGuard.payload(row(AcademicKind.HISTORIES,bad))}
    }
    @Test fun normalizationPreservesTextNewlinesAndLegacyWeekendSlots() {
        val text="História & ação\n\nPontuação; ‘acentos’ — fim\n".repeat(100)
        val r=LessonRecord("r",c.id,"2026-10-20",text,text)
        val p=AcademicRecords.json.encodeToJsonElement(LessonRecord.serializer(),r).jsonObject
        assertEquals(p,PhaseBGuard.normalized(row(AcademicKind.JOURNAL,p)).payload)
        val t=AcademicRecords.json.encodeToJsonElement(WeeklyTimetable.serializer(),revision()).jsonObject
        assertEquals(t,PhaseBGuard.normalized(row(AcademicKind.TIMETABLES,t)).payload)
        Timetables.validate(listOf(revision())) // Read compatibility, not permission to create weekends.
    }
    @Test fun confirmedTimetableCannotBeChangedDeletedOrRenumbered() {
        val before=empty.copy(timetables=listOf(revision()))
        assertBlocked{PhaseDGuard.immutable(before,empty)}
        assertBlocked{PhaseDGuard.immutable(before,before.copy(timetables=listOf(revision().copy(revision=2))))}
        PhaseDGuard.immutable(before,before.copy(timetables=before.timetables+revision().copy(id="next",revision=2)))
    }
    @Test fun movedEnrollmentRequiresCompleteChain() {
        val moved=s.copy(status="Remanejado",left="2026-10-19")
        val snapshot=empty.copy(classes=listOf(c),students=listOf(moved))
        assertBlocked{PhaseDGuard.validate(empty,snapshot)}
    }
    @Test fun correctionPrefixCannotBeRewrittenOrLostWhenMovedToHistory() {
        val correction=RelocationCorrection("audit","2026-10-22T10:00:00Z","2026-10-21","2026-10-20","Externo anterior","Externo corrigido",emptyList())
        val link=Relocation(s.id,null,"2026-10-20","Externo corrigido",listOf(correction))
        val before=empty.copy(relocations=listOf(link))
        assertBlocked{PhaseDGuard.immutable(before,before.copy(relocations=listOf(link.copy(corrections=emptyList()))))}
        val h=PreservedHistory(c.id,HistoricalContent(c,listOf(s),emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),listOf(link)))
        PhaseDGuard.immutable(before,empty.copy(histories=listOf(h)))
        val changed=link.copy(corrections=listOf(correction.copy(previousTarget="Alterado")))
        val badHistory=h.copy(content=h.content.copy(relocations=listOf(changed)))
        assertBlocked{PhaseDGuard.immutable(before,empty.copy(histories=listOf(badHistory)))}
    }
    @Test fun externalDateCannotChangeWithoutNewAdministrativeAudit() {
        val link=Relocation(s.id,null,"2026-10-20","Externo")
        val before=empty.copy(students=listOf(s),relocations=listOf(link))
        assertBlocked{PhaseDGuard.immutable(before,before.copy(relocations=listOf(link.copy(effectiveDate="2026-10-21"))))}
    }
    @Test fun sourceLinkCannotDisappearWhileEitherEnrollmentSurvives() {
        val link=Relocation(s.id,"dest","2026-10-20")
        val before=empty.copy(students=listOf(s),relocations=listOf(link))
        assertBlocked{PhaseDGuard.immutable(before,before.copy(relocations=emptyList()))}
        PhaseDGuard.immutable(before,empty) // Explicit deletion of the whole component keeps remote versions.
    }
}
