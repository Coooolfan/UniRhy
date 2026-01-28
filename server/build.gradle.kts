import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.spring)
  alias(libs.plugins.spring.boot)
  alias(libs.plugins.spring.dependency.management)
  alias(libs.plugins.graalvm.native)
  alias(libs.plugins.ksp)
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
  implementation(libs.spring.boot.starter.webmvc) {
    exclude(group = "org.springframework.boot", module = "spring-boot-jackson")
  }
  implementation(libs.spring.boot.jackson2)
  implementation(libs.spring.boot.starter.flyway)

  implementation(libs.jackson.module.kotlin)
  implementation(libs.kotlin.reflect)

  implementation(libs.jimmer.spring.boot.starter)
  ksp(libs.jimmer.ksp)

  implementation(libs.sa.token.starter)
  implementation(libs.spring.security.crypto)

  implementation(libs.jAudioTagger)

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


tasks.withType<Jar> {
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.withType<BootJar> {
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
