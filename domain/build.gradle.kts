import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Domain layer: pure Kotlin (model, repository interfaces, use cases).
// No Android dependency, so every test here runs on the JVM.
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
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(project(":testing"))
    testImplementation(libs.bundles.unit.test)
}
