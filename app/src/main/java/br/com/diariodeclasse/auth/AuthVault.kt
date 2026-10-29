package br.com.diariodeclasse.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Serializable data class AuthSecrets(val session:AuthSession?=null,val pending:PendingLink?=null,val legacyOwner:String?=null) {
    override fun toString()="AuthSecrets(redacted)"
}
interface AuthVault {
    fun read():AuthSecrets
    fun write(value:AuthSecrets)
    fun clearSession(){write(read().copy(session=null,pending=null))}
    fun bindOwner(owner:String){write(read().copy(legacyOwner=owner))}
}
/** No-backup + Android Keystore. Corruption is a lock, never a fresh unowned installation. */
class KeystoreAuthVault(context:Context):AuthVault {
    private val file=AtomicFile(File(context.noBackupFilesDir,"noctua-auth-v1.sealed"))
    private val ownerFile=AtomicFile(File(context.noBackupFilesDir,"noctua-owner-v1.sealed"))
    private val alias="noctua.auth.storage.v1"
    private fun key():SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        (store.getKey(alias,null) as? SecretKey)?.let{return it}
        check(!exists(file)&&!exists(ownerFile)) { "Proteção local indisponível. Os dados acadêmicos foram preservados." }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun decrypt(source:AtomicFile):ByteArray {
        val bytes=source.readFully();check(bytes.size in 29..524288)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)))
        cipher.updateAAD("noctua.auth.v1:${source.baseFile.name}".toByteArray())
        return cipher.doFinal(bytes.copyOfRange(12,bytes.size))
    }
    private fun exists(source:AtomicFile)=source.baseFile.exists()||File(source.baseFile.path+".bak").exists()
    private fun owner():String?=if(exists(ownerFile))decrypt(ownerFile).toString(Charsets.UTF_8)else null
    @Synchronized override fun read():AuthSecrets {
        val result=if(exists(file))Json.decodeFromString<AuthSecrets>(decrypt(file).toString(Charsets.UTF_8))else AuthSecrets()
        return result.copy(legacyOwner=owner())
    }
    private fun encrypt(target:AtomicFile,plain:ByteArray) {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());cipher.updateAAD("noctua.auth.v1:${target.baseFile.name}".toByteArray())
        val bytes=cipher.iv+cipher.doFinal(plain)
        val output=target.startWrite()
        try {output.write(bytes);target.finishWrite(output)} catch(e:Exception){target.failWrite(output);throw e}
    }
    @Synchronized override fun write(value:AuthSecrets) {
        check(value.legacyOwner==owner())
        encrypt(file,Json.encodeToString(value.copy(legacyOwner=null)).toByteArray())
    }
    @Synchronized override fun clearSession(){file.delete();check(!exists(file))}
    @Synchronized override fun bindOwner(owner:String) {
        require(this.owner()==null||this.owner()==owner)
        encrypt(ownerFile,owner.toByteArray(Charsets.UTF_8))
    }
}
