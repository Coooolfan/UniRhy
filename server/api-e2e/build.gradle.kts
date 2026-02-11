plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = rootProject.group
version = rootProject.version

kotlin {
    jvmToolchain(25)
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.springBoot.get()}"))
    testImplementation(project(":"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(libs.jimmer.spring.boot.starter)
    testImplementation(libs.jAudioTagger)
    testImplementation(libs.test.kotlin)
    testRuntimeOnly(libs.test.launcher)
}

tasks.test {
    useJUnitPlatform()
}
