package br.com.diariodeclasse

import br.com.diariodeclasse.domain.CsvRoster
import org.junit.Assert.*
import org.junit.Test

class CsvRosterTest {
    @Test fun bomAccentsReorderedColumnsAndExplicitNumbers() {
        val rows = CsvRoster.parse("\uFEFFNome completo;Turma;Número\r\nEstudante Fictício Beta;Exemplo;18\r\nEstudante Fictício Alfa;Exemplo;3")
        assertEquals(listOf(18, 3), rows.map { it.number })
        assertEquals("Estudante Fictício Beta", rows.first().name)
    }
    @Test fun quotesDelimiterInsideNameAndEscapedQuotes() {
        val rows = CsvRoster.parse("numero,nome\n5,\"Estudante, Fictício \"\"Alfa\"\"\"")
        assertEquals(5, rows.single().number)
        assertEquals("Estudante, Fictício \"Alfa\"", rows.single().name)
    }
    @Test fun pastedListPreservesOrderAndOptionalNumbers() {
        val rows = CsvRoster.parse("18 Estudante Fictício Beta\n\n3. Estudante Fictício Alfa\nEstudante Fictício Gama")
        assertEquals(listOf(18, 3, null), rows.map { it.number })
        assertEquals("Estudante Fictício Gama", rows.last().name)
    }
    @Test fun tabSeparatedWithoutHeader() {
        assertEquals(7, CsvRoster.parse("7\tEstudante Fictício Alfa").single().number)
    }
    @Test fun missingNamesAndInvalidNumbersNeverSilentlyDisappear() {
        listOf("nome;numero\n;1", "nome;numero\nEstudante Fictício;0", "nome;numero\nEstudante Fictício;x", "nome;numero\nEstudante Fictício;9999999999999", "", "nome;numero\nEstudante Fictício").forEach { input ->
            try { CsvRoster.parse(input); fail("Deveria rejeitar entrada inválida") } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun ambiguousColumnsAndMalformedQuotesRequireCorrection() {
        listOf("texto;outra coluna;mais", "nome\n\"não fechada", "nome\n\"Estudante\"texto").forEach {
            try { CsvRoster.parse(it); fail() } catch (_: IllegalArgumentException) { }
        }
    }
}
