import java.security.KeyStore
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}
android {
    namespace = "br.com.diariodeclasse"
    compileSdk = 35
    defaultConfig {
        applicationId = "br.com.diariodeclasse"
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "0.6.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
