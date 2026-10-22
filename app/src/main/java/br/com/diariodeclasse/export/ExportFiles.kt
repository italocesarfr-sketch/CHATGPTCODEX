package br.com.diariodeclasse.export

import java.io.*

/** A prévia confirmada vira um arquivo privado temporário; o seletor nunca troca sua turma/dados. */
object ExportFiles {
    fun prepare(folder:File,table:ExportTable,format:ExportFormat):File {
        check(folder.isDirectory||folder.mkdirs()) {"Não foi possível preparar a exportação."}
        val file=File.createTempFile("noctua-",".${format.extension}",folder)
        try {file.outputStream().use{when(format) {
            ExportFormat.XLSX -> SpreadsheetWriter.xlsx(table,it)
            ExportFormat.CSV -> SpreadsheetWriter.csv(table,it)
            ExportFormat.TSV -> it.write(SpreadsheetWriter.clipboard(table).toByteArray(Charsets.UTF_8))
        }};return file}
        catch(e:Exception){file.delete();throw e}
    }
    /** true só depois de copiar, flush E close. Cancelamento não abre destino nem declara sucesso. */
    fun deliver(file:File,confirmed:Boolean,open:()->OutputStream?):Boolean {
        try {
            if(!confirmed)return false
            val output=open()?:throw IOException("Não foi possível abrir o arquivo de destino.")
            output.use{out->file.inputStream().use{it.copyTo(out)};out.flush()}
            return true
        }finally{file.delete()}
    }
}
