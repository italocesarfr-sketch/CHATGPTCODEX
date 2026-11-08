package br.com.diariodeclasse.auth

import java.security.MessageDigest

/** Validates build identity before any hosted request. No URL/key fallback. */
object AuthEnvironment {
    const val HOMOLOGATION_PROJECT = "sscexzxtqdcsqdlcytum"
    const val HOMOLOGATION_ID = "br.com.diariodeclasse.homologacao"
    fun validate(homologation:Boolean,debug:Boolean,applicationId:String,project:String,
                 url:String,key:String,callbackScheme:String,productionKeyFingerprint:String) {
        fun valid(condition:Boolean) = require(condition) { "Configuração de autenticação inválida. Os dados locais foram preservados." }
        valid(project.matches(Regex("[a-z0-9]{20}")))
        valid(url=="https://$project.supabase.co")
        valid(key.matches(Regex("sb_publishable_[A-Za-z0-9_-]{16,200}")))
        valid(callbackScheme==applicationId)
        if(homologation) {
            valid(debug && applicationId==HOMOLOGATION_ID && project==HOMOLOGATION_PROJECT)
            val fingerprint=MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString(""){"%02x".format(it)}
            valid(productionKeyFingerprint.matches(Regex("[0-9a-f]{64}")) && fingerprint!=productionKeyFingerprint)
        }else valid(applicationId=="br.com.diariodeclasse")
    }
}
