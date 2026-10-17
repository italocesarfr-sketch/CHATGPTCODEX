package br.com.diariodeclasse

import br.com.diariodeclasse.backup.*
import kotlinx.serialization.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class BackupCodecTest {
    private fun fixture(): BackupSnapshot = BackupCodec.json.decodeFromString(requireNotNull(javaClass.classLoader?.getResourceAsStream("baseline-v1.json")).bufferedReader().use { it.readText() })
    private fun expectFailure(action: () -> Unit) {
        try { action(); fail("Deveria rejeitar") } catch (_: BackupFailure) { }
    }
    @Test fun roundTripAllTablesAndUnicode() {
        val value = fixture().canonical()
        val bytes = BackupCodec.encode(value, 2, null)
        assertEquals(value, BackupCodec.decode(bytes).snapshot)
        assertEquals(0.0, value.grades.first { it.studentId == "alfa" && it.activityId == "atividade-1" }.value!!, 0.0)
        assertNull(value.grades.first { it.studentId == "beta-origem" }.value)
        assertEquals(0, value.drafts.first { it.id == "rascunho-incompleto" }.number)
    }
    @Test fun readsLegacyFormatOneAndPreservesNewSelectionInFormatTwo() {
        val value = fixture()
        // Usar um ID efetivo da fixture, sem assumir posições da lista.
        val withSelection = value.copy(selection = br.com.diariodeclasse.data.AppSelection(value.classes.first().year, value.classes.first().id))
        assertEquals(withSelection.canonical(), BackupCodec.decode(BackupCodec.encode(withSelection,3,null)).snapshot)
        val oldPayload = requireNotNull(javaClass.classLoader?.getResourceAsStream("baseline-v1.json")).bufferedReader().use { it.readText() }
        val envelope = BackupEnvelope("diario-de-classe-backup",1,"br.com.diariodeclasse",1,BackupCodec.LEGACY_IDENTITY,"2026-01-01T00:00:00Z",2,null,BackupCodec.hash(oldPayload.toByteArray(Charsets.UTF_8)),oldPayload)
        val decoded = BackupCodec.decode(BackupCodec.json.encodeToString(envelope).toByteArray(Charsets.UTF_8))
        assertNull(decoded.snapshot.selection)
        assertEquals(fixture().canonical(),decoded.snapshot)
    }
    @Test fun emptyDatabaseIsAValidExplicitBackup() {
        val empty = BackupSnapshot(emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList())
        assertEquals(empty, BackupCodec.decode(BackupCodec.encode(empty,2,null)).snapshot)
    }
    @Test fun rejectsCorruptionWrongVersionsAndWrongApplication() {
        val original = BackupCodec.json.decodeFromString<BackupEnvelope>(BackupCodec.encode(fixture(),2,null).toString(Charsets.UTF_8))
        listOf(original.copy(payloadSha256="0".repeat(64)), original.copy(formatVersion=4), original.copy(databaseVersion=3),
            original.copy(applicationId="outro.app"), original.copy(schemaIdentityHash="outro-esquema")).forEach { changed ->
            expectFailure { BackupCodec.decode(BackupCodec.json.encodeToString(changed).toByteArray()) }
        }
        expectFailure { BackupCodec.decode(byteArrayOf()) }; expectFailure { BackupCodec.decode("{ incompleto".toByteArray()) }
    }
    @Test fun rejectsDanglingReferencesAndDuplicatePrimaryKeys() {
        val f=fixture()
        expectFailure { BackupCodec.validate(f.copy(students=emptyList())) }
        expectFailure { BackupCodec.validate(f.copy(classes=f.classes+f.classes.first())) }
        expectFailure { BackupCodec.validate(f.copy(marks=f.marks+f.marks.first())) }
        expectFailure { BackupCodec.validate(f.copy(grades=listOf(f.grades.first().copy(value=100.0)))) }
    }
    @Test fun boundedReaderRejectsOversizedInput() {
        val input=object: java.io.InputStream() {
            private var remaining=BackupCodec.MAX_BYTES+1
            override fun read(): Int { if(remaining-- > 0) return 65; return -1 }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if(remaining<=0) return -1
                val size=minOf(length,remaining); java.util.Arrays.fill(buffer,offset,offset+size,65.toByte());remaining-=size;return size
            }
        }
        expectFailure { BackupCodec.read(input) }
    }
    @Test fun fingerprintDoesNotDependOnArrayOrder() {
        val f=fixture()
        assertEquals(BackupCodec.fingerprint(f),BackupCodec.fingerprint(f.copy(classes=f.classes.reversed(),students=f.students.reversed())))
    }
    @Test fun readerPropagatesFailureRatherThanAcceptingPartialFile() {
        try { BackupCodec.read(object: java.io.InputStream() { override fun read(): Int = throw java.io.IOException("simulada") }); fail() }
        catch (_: java.io.IOException) { }
    }
}
