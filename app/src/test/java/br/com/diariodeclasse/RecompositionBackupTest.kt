package br.com.diariodeclasse

import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.Recomposition
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class RecompositionBackupTest {
    private fun text()=requireNotNull(javaClass.classLoader?.getResourceAsStream("baseline-v1.json")).bufferedReader().use {it.readText()}
    private fun fixture()=BackupCodec.json.decodeFromString<BackupSnapshot>(text())
    private fun fails(action:()->Unit) {try {action();fail("Deveria rejeitar")}catch(_:BackupFailure){}}
    @Test fun newFormatPreservesZeroUnperformedDatesAndCanonicalOrder() {
        val r=Recomposition("alfa",3,5.25,"2026-11-20")
        val before=fixture().copy(recompositions=listOf(r,Recomposition("gama",3,null,"2026-11-21"),Recomposition("alfa",1,0.0,"2026-11-20"))).canonical()
        val decoded=BackupCodec.decode(BackupCodec.encode(before,9,null))
        assertEquals(3,decoded.envelope.formatVersion);assertEquals(2,decoded.envelope.databaseVersion)
        assertEquals(before,decoded.snapshot)
        assertEquals(BackupCodec.fingerprint(before),BackupCodec.fingerprint(before.copy(recompositions=before.recompositions.reversed())))
    }
    @Test fun bothLegacyFormatsLoadWithoutInventingRecompositions() {
        val payload=text()
        for(format in 1..2) {
            val e=BackupEnvelope("diario-de-classe-backup",format,"br.com.diariodeclasse",1,BackupCodec.LEGACY_IDENTITY,"2026-10-17T00:00:00Z",8,null,BackupCodec.hash(payload.toByteArray()),payload)
            val decoded=BackupCodec.decode(BackupCodec.json.encodeToString(e).toByteArray())
            assertTrue(decoded.snapshot.recompositions.isEmpty());assertEquals(fixture().canonical(),decoded.snapshot)
        }
    }
    @Test fun invalidScoresOriginsDuplicateKeysOrDanglingStudentsAreRejected() {
        val f=fixture();val r=Recomposition("alfa",3,5.0,"2026-11-20")
        for(invalid in listOf(r.copy(value=-1.0),r.copy(value=10.01),r.copy(value=Double.NaN),r.copy(studentId="inexistente"),r.copy(term=0),r.copy(date="2026-99-99"))) {
            fails {BackupCodec.validate(f.copy(recompositions=listOf(invalid)))}
        }
        fails {BackupCodec.validate(f.copy(recompositions=listOf(r,r)))}
        fails {BackupCodec.validate(f.copy(recompositions=listOf(r),terms=f.terms.filterNot {it.classId=="classe-alfa"&&it.number==3}))}
    }
    @Test fun legacyEnvelopeCannotSilentlyDiscardNewRecordsAndCorruptionIsRejected() {
        val f=fixture().copy(recompositions=listOf(Recomposition("alfa",3,5.0,"2026-11-20")))
        val e=BackupCodec.decode(BackupCodec.encode(f,9,null)).envelope
        fails {BackupCodec.decode(BackupCodec.json.encodeToString(e.copy(formatVersion=2,databaseVersion=1,schemaIdentityHash=BackupCodec.LEGACY_IDENTITY)).toByteArray())}
        fails {BackupCodec.decode(BackupCodec.json.encodeToString(e.copy(payload=e.payload.replace("5.0","6.0"))).toByteArray())}
        fails {BackupCodec.decode(BackupCodec.json.encodeToString(e.copy(databaseVersion=1)).toByteArray())}
    }
}
