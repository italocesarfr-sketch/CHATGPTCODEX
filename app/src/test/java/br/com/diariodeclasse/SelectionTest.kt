package br.com.diariodeclasse
import br.com.diariodeclasse.data.*
import org.junit.Test
import org.junit.Assert.*
class SelectionTest {
    private val old = SchoolClass(id="old",name="Turma fictícia",subject="História",year=2025)
    private val current = old.copy(id="new",year=2026)
    @Test fun persistedIdAndYearDisambiguateEqualNames() {
        assertEquals(AppSelection(2025,"old"),resolveSelection(AppSelection(2025,"old"),listOf(current,old),2026))
    }
    @Test fun missingIdFallsBackWithinSelectedYear() {
        assertEquals(AppSelection(2025,"old"),resolveSelection(AppSelection(2025,"removed"),listOf(current,old),2026))
        assertEquals(AppSelection(2024,null),resolveSelection(AppSelection(2024,"removed"),listOf(current,old),2026))
    }
    @Test fun firstOpenAndEmptyDatabaseHaveSafeDefaults() {
        assertEquals(AppSelection(2026,"new"),resolveSelection(null,listOf(old,current),2026))
        assertEquals(AppSelection(2026,null),resolveSelection(null,emptyList(),2026))
    }
}
