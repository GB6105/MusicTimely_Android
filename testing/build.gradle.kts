import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Shared hand-written fakes (FakeClock, Fake*Repository, Fake*Store) for :domain and :app tests.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":domain"))
    implementation(libs.kotlinx.coroutines.core)
}
