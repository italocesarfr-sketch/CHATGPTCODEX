package br.com.diariodeclasse.auth

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Versioned portable envelope. Binary ciphertext avoids base64 copies of the academic payload. */
object AccountBackup {
    private val magic="NOCTUA-ACCOUNT-BACKUP-1\n".toByteArray(Charsets.US_ASCII)
    @Serializable private data class Header(val format:String="noctua-account-backup",val version:Int=1,val owner:String,
        val iterations:Int=600000,val salt:String,val iv:String)
    private val json=Json { encodeDefaults=true }
    private fun key(password:CharArray,salt:ByteArray):SecretKeySpec {
        val spec=PBEKeySpec(password,salt,600000,256)
        return try{SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded,"AES")}finally{spec.clearPassword()}
    }
    fun write(output:OutputStream,bytes:ByteArray,owner:String,password:CharArray) {
        require(password.size>=12){"Use pelo menos 12 caracteres na senha do backup."};java.util.UUID.fromString(owner)
        require(bytes.size<=32*1024*1024)
        val salt=ByteArray(16).also{SecureRandom().nextBytes(it)}
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(password,salt))
        val encode=Base64.getEncoder()
        val header=json.encodeToString(Header(owner=owner,salt=encode.encodeToString(salt),iv=encode.encodeToString(cipher.iv))).toByteArray(Charsets.UTF_8)
        val prefix=magic+ByteBuffer.allocate(4).putInt(header.size).array()+header
        cipher.updateAAD(prefix);output.write(prefix)
        val shield=object:java.io.FilterOutputStream(output){override fun close(){flush()}}
        CipherOutputStream(shield,cipher).use{it.write(bytes)}
    }
    fun seal(bytes:ByteArray,owner:String,password:CharArray):ByteArray = java.io.ByteArrayOutputStream().also{write(it,bytes,owner,password)}.toByteArray()
    fun open(bytes:ByteArray,owner:String,password:CharArray):ByteArray {
        try {
            require(bytes.size<=33*1024*1024 && protected(bytes))
            val length=ByteBuffer.wrap(bytes,magic.size,4).int
            require(length in 1..2048 && bytes.size>=magic.size+4+length+16)
            val start=magic.size+4;val end=start+length
            val value=json.decodeFromString<Header>(String(bytes,start,length,Charsets.UTF_8))
            require(value.format=="noctua-account-backup"&&value.version==1&&value.iterations==600000&&value.owner==owner)
            val decode=Base64.getDecoder();val salt=decode.decode(value.salt);val iv=decode.decode(value.iv)
            require(salt.size==16&&iv.size==12)
            val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(password,salt),GCMParameterSpec(128,iv));cipher.updateAAD(bytes,0,end)
            return cipher.doFinal(bytes,end,bytes.size-end)
        } catch(_:Exception){throw AuthFailure(AuthFailure.Kind.INVALID,"Backup de outra conta, senha incorreta ou arquivo inválido.")}
    }
    fun protected(bytes:ByteArray):Boolean = bytes.size>=magic.size+4 && magic.indices.all{bytes[it]==magic[it]}
    fun read(input:InputStream):ByteArray {
        val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
        while(true){val count=input.read(buffer);if(count<0)break;require(output.size()+count<=33*1024*1024){"Arquivo de backup muito grande."};output.write(buffer,0,count)}
        return output.toByteArray()
    }
}
