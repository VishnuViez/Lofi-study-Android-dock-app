// Versions match what is already in this Mac's Gradle cache (AGP 8.11.1,
// Kotlin 2.2.20), so a build needs only the AndroidX libraries downloaded.
plugins {
    id("com.android.application") version "8.11.1" apply false
    id("org.jetbrains.kotlin.android") version "2.2.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20" apply false
} 
