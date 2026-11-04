package br.com.diariodeclasse

import br.com.diariodeclasse.sync.academic.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class AcademicPagingTest {
    @Test fun fiveThousandOrderedIdentitiesSurviveBoundedUnicodeParts() {
        val rows=(1..5000).map{buildJsonObject{put("id","fictitious-$it");put("text","Ação, reflexão\n$it • 👩🏽‍🏫");put("zero",0);put("pending",JsonNull)}}
        val parts=AcademicPaging.split(rows)
        assertTrue(parts.size>32);assertEquals(rows,parts.flatMap{it})
        assertTrue(parts.all{it.size<=128&&it.toString().toByteArray().size<=AcademicPaging.PART_BYTES})
    }
    @Test fun largeSingleHistoryIsIndivisibleAndNoTextIsTruncated() {
        val history=buildJsonObject{put("description","á\n".repeat(170000))}
        val parts=AcademicPaging.split(listOf(history,buildJsonObject{put("small",true)}))
        assertEquals(history,parts.first().first());assertEquals(2,parts.sumOf{it.size})
    }
    @Test fun oversizedHistoryIsRejectedRatherThanSplitOrTruncated() {
        try{AcademicPaging.split(listOf(JsonPrimitive("á".repeat(500000))));fail()}catch(_:IllegalArgumentException){}
    }
    @Test fun oldBatchContractRemainsLimitedUntilEScopeIsExplicit() {
        val rows=(1..300).map{AcademicChange("classes",buildJsonObject{put("id","c-$it")},0,null)}
        val batch=AcademicBatch("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",rows)
        try{batch.validate();fail()}catch(_:IllegalArgumentException){}
        batch.validate(AcademicPaging.MAX_RECORDS)
    }
    @Test fun duplicateIdentityCannotHideAcrossFragments() {
        val r=AcademicChange("classes",buildJsonObject{put("id","c")},0,null)
        try{AcademicBatch("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",listOf(r,r)).validate(AcademicPaging.MAX_RECORDS);fail()}catch(_:IllegalArgumentException){}
    }
    @Test fun accountLargerThanAtomicLimitStillHasASmallIncrementalChange() {
        val base=(1..20000).map{n->AcademicRow("lesson_records",buildJsonObject{put("id","j-$n")},buildJsonObject{put("id","j-$n");put("description","Texto")})}
        val local=base.mapIndexed{i,row->if(i==123)row.copy(payload=JsonObject(row.payload+("description" to JsonPrimitive("Edição"))))else row}
        val delta=AcademicRecords.delta(base,local,base.associate{it.identity to 1L})
        assertEquals(1,delta.size);assertEquals(1,AcademicPaging.parts(AcademicBatch("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",delta)).size)
    }
    @Test fun malformedFragmentCannotAdvanceTransferProgress() {
        val row=AcademicVersion("classes",buildJsonObject{put("id","c")},1,null)
        for(part in listOf(AcademicFragment("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",1,1,1,listOf(row)),
            AcademicFragment("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",1,2,0,listOf(row,row)))) {
            try{part.validate();fail()}catch(_:IllegalArgumentException){}
        }
    }
}
