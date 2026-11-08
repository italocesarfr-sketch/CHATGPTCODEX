import java.security.KeyStore
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}
// Public client configuration. Never read administrative/session credentials here.
val productionProject = "mpsgdchpgyxoqowfjoqm"
val productionPublishable = "sb_publishable_61hfac3DL6eLG2gRBpEIeg_zBd1d_mZ"
val homologacaoProject = "sscexzxtqdcsqdlcytum"
val homologacaoKeyInput = providers.environmentVariable("NOCTUA_HOMOLOGACAO_PUBLISHABLE_KEY")
    .orElse(providers.gradleProperty("noctua.homologacao.publishableKey")).orNull.orEmpty()
val homologacaoKeyValid = homologacaoKeyInput.matches(Regex("sb_publishable_[A-Za-z0-9_-]{16,200}")) &&
    homologacaoKeyInput != productionPublishable
// Invalid input is never embedded, even in generated classes for unit tests.
val homologacaoPublishable = if (homologacaoKeyValid) homologacaoKeyInput else ""
val productionKeyFingerprint = MessageDigest.getInstance("SHA-256").digest(productionPublishable.toByteArray()).joinToString("") { "%02x".format(it) }
android {
    namespace = "br.com.diariodeclasse"
    compileSdk = 35
    defaultConfig {
        applicationId = "br.com.diariodeclasse"
        minSdk = 26
        targetSdk = 35
        versionCode = 45
        versionName = "0.22.0-dev-e2"
        testInstrumentationRunner = "br.com.diariodeclasse.AuthTestRunner"
        manifestPlaceholders["authCallbackScheme"] = "br.com.diariodeclasse"
        buildConfigField("boolean", "HOMOLOGACAO", "false")
        buildConfigField("String", "SUPABASE_PROJECT_REF", "\"$productionProject\"")
        buildConfigField("String", "SUPABASE_URL", "\"https://$productionProject.supabase.co\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"$productionPublishable\"")
        buildConfigField("String", "AUTH_CALLBACK_SCHEME", "\"br.com.diariodeclasse\"")
        buildConfigField("String", "PRODUCTION_KEY_FINGERPRINT", "\"$productionKeyFingerprint\"")
    }
    buildTypes.create("homologacao") {
        initWith(buildTypes.getByName("debug"))
        isDebuggable = true
        signingConfig = signingConfigs.getByName("debug")
        applicationIdSuffix = ".homologacao"
        versionNameSuffix = "-homologacao"
        matchingFallbacks += listOf("debug")
        manifestPlaceholders["authCallbackScheme"] = "br.com.diariodeclasse.homologacao"
        buildConfigField("boolean", "HOMOLOGACAO", "true")
        buildConfigField("String", "SUPABASE_PROJECT_REF", "\"$homologacaoProject\"")
        buildConfigField("String", "SUPABASE_URL", "\"https://$homologacaoProject.supabase.co\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"$homologacaoPublishable\"")
        buildConfigField("String", "AUTH_CALLBACK_SCHEME", "\"br.com.diariodeclasse.homologacao\"")
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    sourceSets.getByName("test").resources.srcDir("$projectDir/../test-fixtures")
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/../test-fixtures")
    testOptions { unitTests.isReturnDefaultValues = true }
}
configurations.getByName("homologacaoImplementation").extendsFrom(configurations.getByName("debugImplementation"))
val validateHomologacaoConfiguration = tasks.register("validateHomologacaoConfiguration") {
    doLast {
        check(homologacaoKeyValid) {
            "Homologação bloqueada: configure NOCTUA_HOMOLOGACAO_PUBLISHABLE_KEY com a chave pública do projeto de homologação. Sem fallback para produção."
        }
    }
}
// Unit tests/manifest inspection can compile without a key. Packaging/install cannot.
tasks.matching { it.name in setOf("packageHomologacao", "assembleHomologacao", "installHomologacao") }.configureEach {
    dependsOn(validateHomologacaoConfiguration)
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.core:core-ktx:1.15.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

// A assinatura estável é opcional para testes debug, mas obrigatória para qualquer release.
val releaseNames = listOf("DIARY_RELEASE_STORE_FILE", "DIARY_RELEASE_STORE_PASSWORD", "DIARY_RELEASE_KEY_ALIAS", "DIARY_RELEASE_KEY_PASSWORD", "DIARY_RELEASE_CERT_SHA256")
val releaseValues = releaseNames.associateWith { providers.environmentVariable(it).orNull }
val stableSigningReady = releaseValues.values.all { !it.isNullOrBlank() }
android {
    if (stableSigningReady) {
        signingConfigs.create("stable") {
            storeType = "PKCS12"
            storeFile = file(releaseValues.getValue("DIARY_RELEASE_STORE_FILE")!!)
            storePassword = releaseValues.getValue("DIARY_RELEASE_STORE_PASSWORD")
            keyAlias = releaseValues.getValue("DIARY_RELEASE_KEY_ALIAS")
            keyPassword = releaseValues.getValue("DIARY_RELEASE_KEY_PASSWORD")
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("stable")
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst {
        check(stableSigningReady) { "Release bloqueado: configure a chave estável e o certificado esperado. Não há fallback para chave temporária." }
        try {
            val store = KeyStore.getInstance("PKCS12")
            file(releaseValues.getValue("DIARY_RELEASE_STORE_FILE")!!).inputStream().use {
                store.load(it, releaseValues.getValue("DIARY_RELEASE_STORE_PASSWORD")!!.toCharArray())
            }
            check(store.isKeyEntry(releaseValues.getValue("DIARY_RELEASE_KEY_ALIAS")!!))
            val cert = requireNotNull(store.getCertificate(releaseValues.getValue("DIARY_RELEASE_KEY_ALIAS")!!))
            (cert as java.security.cert.X509Certificate).checkValidity()
            val actual = MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString("") { "%02x".format(it) }
            val expected = releaseValues.getValue("DIARY_RELEASE_CERT_SHA256")!!.replace(":", "").lowercase()
            check(expected.matches(Regex("[0-9a-f]{64}")))
            check(actual == expected) { "Certificado diferente do esperado." }
        } catch (_: Exception) { throw GradleException("Release bloqueado: chave ou certificado esperado não confere.") }
    }
}
