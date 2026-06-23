plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.2.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20" apply false
    id("com.google.devtools.ksp") version "2.2.20-2.0.4" apply false
    // Firebase — apply false here; enabled per-module only when google-services.json is present
    id("com.google.gms.google-services") version "4.4.2" apply false
}
