package br.com.diariodeclasse.export

import br.com.diariodeclasse.data.*
import kotlinx.serialization.Serializable

/** Estado de apresentação, sem persistência em Room ou regras acadêmicas próprias. */
enum class SpreadsheetKind(val label: String) {
    NOTES("Notas"), FREQUENCY("Frequência"), CONSOLIDATED("Consolidado"),
    ATTENDANCE("Chamada"), PLANS("Planos de aula"), ANNUAL("Resumo anual");
    val regular get() = this in listOf(NOTES, FREQUENCY, CONSOLIDATED)
    val ranged get() = this == ATTENDANCE || this == PLANS
}

@Serializable
data class SpreadsheetPreferences(
    val format: ExportFormat = ExportFormat.XLSX,
    val headers: Boolean = true,
    val callNumber: Boolean = true,
    val allTerms: Boolean = false,
    val customRange: Boolean = false,
    val start: String = "",
    val end: String = "",
    val copyMode: String = "Dados completos",
    val share: Boolean = false,
    val term: Int? = null
)

/** Adapta apenas parâmetros/colunas das projeções existentes. A matriz serve arquivo, cópia e prévia. */
object ExportPresentation {
    fun build(data: DiarySnapshot, c: SchoolClass, term: Int, kind: SpreadsheetKind, p: SpreadsheetPreferences): ExportTable {
        val selectedTerm = p.term ?: term
        require(selectedTerm in 1..4)
        if (kind.regular) return ExportProjection.build(data, c, ExportOptions(
            ExportModel.valueOf(kind.name), selectedTerm.takeUnless { p.allTerms }, p.headers, p.callNumber))
        val source = when (kind) {
            SpreadsheetKind.ANNUAL -> AnnualExportProjection.build(data, c)
            SpreadsheetKind.ATTENDANCE, SpreadsheetKind.PLANS -> {
                val periods = data.terms.filter { it.classId == c.id && (p.allTerms || it.number == selectedTerm) }.sortedBy { it.number }
                if (!p.customRange) require(periods.size == if(p.allTerms) 4 else 1) { "Configure os bimestres ou escolha um intervalo de datas nos ajustes." }
                val start = if (p.customRange) p.start else periods.minOf { it.start }
                val end = if (p.customRange) p.end else periods.maxOf { it.end }
                if (kind == SpreadsheetKind.PLANS) LessonPlanExportProjection.build(data, c, start, end)
                else AttendanceExportProjection.build(data, c, start, end)
            }
            else -> error("Modelo inválido")
        }
        if (kind == SpreadsheetKind.PLANS) return source
        val indices = source.columns.indices.filter { p.callNumber || source.columns[it].key != "number" }
        return source.copy(options = source.options.copy(headers = p.headers, callNumber = p.callNumber),
            columns = indices.map { source.columns[it] }, records = source.records.map { row -> row.copy(cells = indices.map { row.cells[it] }) })
    }

    fun copy(table: ExportTable, p: SpreadsheetPreferences): String = SpreadsheetWriter.clipboard(
        table, p.copyMode != "Dados completos", p.copyMode == "Todas as notas")
}
