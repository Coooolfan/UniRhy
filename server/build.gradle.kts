plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21"
    id("org.springframework.boot") version "4.0.0"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.graalvm.buildtools.native") version "0.11.3"
    id("com.google.devtools.ksp") version "2.3.0"
}

group = "com.coooolfan"
version = "0.0.1"
description = "just listen"

java {
    toolchain {
        // Kotlin has not yet full support for Java 25
        languageVersion = JavaLanguageVersion.of(24)
    }
}

repositories {
    mavenCentral()
}

val jimmerVersion = "0.9.116"
val saTokenVersion = "1.44.0"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.babyfish.jimmer:jimmer-spring-boot-starter:${jimmerVersion}")
    ksp("org.babyfish.jimmer:jimmer-ksp:${jimmerVersion}")
    implementation("cn.dev33:sa-token-spring-boot3-starter:${saTokenVersion}")

    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
