plugins {
    id("com.android.library")
}

android {
    namespace = "io.github.muntashirakon.adb"
    compileSdk = 37
    defaultConfig { minSdk = 34 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.annotation:annotation:1.9.1")
    implementation("org.bouncycastle:bcprov-jdk15to18:1.81")
    implementation("org.conscrypt:conscrypt-android:2.8.0")
    implementation("com.github.MuntashirAkon.spake2-java:spake2-android:2.2.1")
}
