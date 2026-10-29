package br.com.diariodeclasse.auth

import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import br.com.diariodeclasse.DiaryApplication

/** A previously granted URI cannot serve another account's cached export. */
class AccountExportProvider:FileProvider() {
    private fun authorize(uri:Uri) {
        val app=requireNotNull(context).applicationContext as DiaryApplication
        val scope=app.workspace
        check(app.auth.canUse(scope.user.id))
        val parts=uri.pathSegments
        check(parts.size==4&&parts[0]=="planilhas"&&parts[1]==scope.namespace&&parts[2]=="exports-share"&&parts[3].startsWith("noctua-")) {"A sessão deste arquivo foi encerrada."}
    }
    override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor {
        require(mode=="r");authorize(uri)
        return requireNotNull(super.openFile(uri,mode))
    }
    override fun getType(uri:Uri):String? {authorize(uri);return super.getType(uri)}
    override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):android.database.Cursor {
        authorize(uri);return super.query(uri,projection,selection,selectionArgs,sortOrder)
    }
}
