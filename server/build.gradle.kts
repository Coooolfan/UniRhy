import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
//  alias(libs.plugins.graalvm.native)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ben.manes.versions)
}

group = providers.gradleProperty("projectGroup").get()
version = providers.gradleProperty("projectVersion").get()
description = "just listen"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.websocket)
    implementation(libs.spring.boot.jackson)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.flyway.core)

    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlin.reflect)

    implementation(libs.jimmer.spring.boot.starter)
    ksp(libs.jimmer.ksp)

    implementation(libs.sa.token.starter) {
        exclude(group = "cn.dev33", module = "sa-token-jackson")
    }
    implementation(libs.sa.token.jackson3)
    implementation(libs.sa.token.jwt)
    implementation(libs.spring.security.crypto)

    implementation(libs.jAudioTagger)
    implementation(libs.s3)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.chicory.runtime)
    implementation(libs.chicory.wasm)

    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.postgresql.flyway)

    testImplementation(libs.test.spring.boot.starter.flyway)
    testImplementation(libs.test.spring.boot.starter.webmvc)
    testImplementation(libs.test.kotlin)
    testRuntimeOnly(libs.test.launcher)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

val envKeys = parseEnvFile(rootProject.file(".env.example")).keys
val envValues = parseEnvFile(rootProject.file(".env"))
val systemEnv = System.getenv()

allprojects {
    tasks.configureEach {
        if (this is ProcessForkOptions) {
            envKeys.forEach { key ->
                if (systemEnv[key].isNullOrBlank()) {
                    val value = envValues[key]
                    if (!value.isNullOrBlank()) {
                        environment(key, value)
                    }
                }
            }
        }
    }
}


tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.withType<BootJar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.named<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask>("dependencyUpdates") {
    rejectVersionIf {
        isNonStable(candidate.version) && !isNonStable(currentVersion)
    }
}

fun parseEnvFile(file: File): Map<String, String> {
    if (!file.exists()) return emptyMap()
    return file.readLines()
        .mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@mapNotNull null
            val index = trimmed.indexOf('=')
            if (index <= 0) return@mapNotNull null
            val key = trimmed.substring(0, index).trim()
            val rawValue = trimmed.substring(index + 1).trim()
            val value = rawValue.removeSurrounding("\"").removeSurrounding("'")
            if (key.isEmpty()) null else key to value
        }
        .toMap()
}

fun isNonStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
    val stableVersion = "^[0-9,.v-]+(-r)?$".toRegex().matches(version)
    return !stableKeyword && !stableVersion
}
