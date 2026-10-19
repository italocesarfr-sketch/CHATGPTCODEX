package br.com.diariodeclasse.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Somente uma planilha temporária, nunca o banco ou o diretório de backups. */
object SpreadsheetShare {
    fun prepare(context:Context,table:ExportTable):File {
        val folder=File(context.cacheDir,"exports-share");check(folder.isDirectory||folder.mkdirs())
        val cutoff=System.currentTimeMillis()-24*60*60*1000L
        folder.listFiles()?.filter{it.isFile&&it.name.startsWith("noctua-")&&it.lastModified()<cutoff}?.forEach{it.delete()}
        return ExportFiles.prepare(folder,table,ExportFormat.XLSX)
    }
    fun intent(context:Context,file:File):Intent {
        val uri=FileProvider.getUriForFile(context,"${context.packageName}.exports",file)
        return Intent(Intent.ACTION_SEND).apply {
            type=ExportFormat.XLSX.mime;putExtra(Intent.EXTRA_STREAM,uri)
            clipData=ClipData.newUri(context.contentResolver,"Chamada • Noctua",uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
