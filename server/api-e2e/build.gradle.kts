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
    testImplementation(libs.spring.boot.starter.webmvc)
    testImplementation(libs.jimmer.spring.boot.starter)
    testImplementation(libs.sa.token.starter)
    testImplementation(libs.jAudioTagger)
    testImplementation(libs.test.kotlin)
    testRuntimeOnly(libs.test.launcher)
}

tasks.test {
    useJUnitPlatform()
    systemProperty("e2e.coverage.write", "false")
}

tasks.register<Test>("generateCoverageMatrix") {
    group = "verification"
    description = "Generate api-e2e coverage matrix markdown file."
    testClassesDirs = tasks.test.get().testClassesDirs
    classpath = tasks.test.get().classpath
    useJUnitPlatform()
    systemProperty("e2e.coverage.write", "true")
    filter {
        includeTestsMatching("com.unirhy.e2e.support.matrix.ApiCoverageMatrixSyncTest")
    }
}
