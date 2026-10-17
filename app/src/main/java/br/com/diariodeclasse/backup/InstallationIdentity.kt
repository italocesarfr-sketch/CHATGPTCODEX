package br.com.diariodeclasse.backup

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import br.com.diariodeclasse.BuildConfig

object InstallationIdentity {
    @Suppress("DEPRECATION")
    fun certificate(context: Context): String? = runCatching {
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
        } else context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
        signatures?.singleOrNull()?.toByteArray()?.let { BackupCodec.hash(it) }
    }.getOrNull()
    fun description(context: Context): String = "Versão ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) • banco Room 1\n${context.packageName}\nCertificado SHA-256:\n${certificate(context) ?: "Não foi possível consultar"}"
}
