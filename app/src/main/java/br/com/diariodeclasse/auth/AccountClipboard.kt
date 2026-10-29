package br.com.diariodeclasse.auth

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import br.com.diariodeclasse.DiaryApplication

object AccountClipboard {
    fun copy(context:Context,owner:String,label:String,text:String) {
        val app=context.applicationContext as DiaryApplication
        check(app.workspace.user.id==owner&&app.auth.canUse(owner))
        val clip=ClipData.newPlainText("Noctua • $label",text)
        clip.description.extras=PersistableBundle().apply{putBoolean("android.content.extra.IS_SENSITIVE",true)}
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
    }
    fun clear(context:Context) {
        val clipboard=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if(clipboard.primaryClipDescription?.label?.toString()?.startsWith("Noctua •")==true) {
            if(Build.VERSION.SDK_INT>=28)clipboard.clearPrimaryClip()else clipboard.setPrimaryClip(ClipData.newPlainText("",""))
        }
    }
}
