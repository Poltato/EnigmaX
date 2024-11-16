plugins {
    // Apply the Android library plugin
    id("dialogue.android.library")
    // Apply the Jacoco plugin for code coverage
    id("dialogue.android.library.jacoco")
    // Apply the Kotlin Kapt plugin for annotation processing
    kotlin("kapt")
    // Apply the Dagger Hilt plugin for dependency injection
    id("dagger.hilt.android.plugin")
    // Apply the Spotless plugin for code formatting
    id("dialogue.spotless")
}

dependencies {
    // Smack library for XMPP over TCP
    implementation(libs.smack.tcp)
    // Smack library for Android extensions
    api(libs.smack.android.extensions)

    // Kotlin coroutines for Android
    implementation(libs.kotlinx.coroutines.android)

    // Dagger Hilt for dependency injection
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    // OMEMO dependencies for end-to-end encryption
    implementation("org.igniterealtime.smack:smack-omemo:4.4.4")
    implementation("org.igniterealtime.smack:smack-omemo-signal:4.4.4")
    implementation("org.igniterealtime.smack:smack-extensions:4.4.4")

    // Exclude xpp3 module to avoid conflicts
    configurations {
        all {
            exclude(group = "xpp3", module = "xpp3")
        }
    }
}