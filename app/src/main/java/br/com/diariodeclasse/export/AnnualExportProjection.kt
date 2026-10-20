package br.com.diariodeclasse.export

import br.com.diariodeclasse.data.*

/** Frequência anual existente, sem médias novas; visibilidade preservada e ordem oficial. */
object AnnualExportProjection {
    fun build(data: DiarySnapshot, c: SchoolClass): ExportTable {
        val source = ExportProjection.build(data, c, ExportOptions(model=ExportModel.FREQUENCY, term=null))
        val roster = data.roster(c.id).associateBy { it.id }
        val records = source.records.filter { it.enrollmentId != null }.sortedWith(
            compareBy<ExportRecord> { roster.getValue(requireNotNull(it.enrollmentId)).number }
                .thenBy { roster.getValue(requireNotNull(it.enrollmentId)).name }.thenBy { it.enrollmentId })
        val columns = source.columns.take(2) + listOf(ExportColumn("class", "Turma"), ExportColumn("year", "Ano letivo")) + source.columns.drop(2)
        return source.copy(title="Noctua • ${c.name} • ${c.subject} • ${c.year} • Relatório anual de frequência", columns=columns,
            records=records.map { it.copy(cells=it.cells.take(2)+listOf(ExportCell.text(c.name), ExportCell.number(c.year, NumberStyle.INTEGER))+it.cells.drop(2)) })
    }
}
