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
  testImplementation(libs.test.kotlin)
  testRuntimeOnly(libs.test.launcher)
}

tasks.test {
  useJUnitPlatform()
}
