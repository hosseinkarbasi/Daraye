plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
 namespace = "com.darayi.app"
 compileSdk = 35
    defaultConfig { applicationId = "com.darayi.app"; minSdk = 26; targetSdk = 35; versionCode = 8; versionName = "2.5" }
 signingConfigs { getByName("debug") { storeFile = rootProject.file("signing/debug.keystore"); storePassword = "android"; keyAlias = "androiddebugkey"; keyPassword = "android" } }
 buildFeatures { compose = true }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
dependencies {
 implementation("androidx.activity:activity-compose:1.10.1")
 implementation("androidx.compose.ui:ui:1.8.3")
 implementation("androidx.compose.foundation:foundation:1.8.3")
 implementation("androidx.compose.material3:material3:1.5.0-alpha01")
 implementation("androidx.compose.material:material-icons-core:1.7.8")
 implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
 implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.1")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
 testImplementation("junit:junit:4.13.2")
 testImplementation("org.json:json:20240303")
}
