plugins {
  id("java-library")
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish")
}

repositories {
  mavenCentral()
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(21))
  }
  withSourcesJar()
}

mavenPublishing {
  configure(com.vanniktech.maven.publish.KotlinJvm())
}

dependencies {
  implementation(project(":common"))
  implementation(libs.arrowCore)
  implementation(libs.bitcoinj)
  implementation(libs.domainApi)
  implementation(libs.guice)
  implementation(libs.guiceAssistedInject)
  implementation(libs.jodaMoney)
  implementation(libs.kfsm)
  implementation(libs.kotlinLogging)
  implementation(libs.kotlinReflect)
  implementation(libs.quiverLib)

  testImplementation(libs.kotestProperty)
  testImplementation(libs.kotestAssertions)
  testImplementation(libs.kotestJunitRunnerJvm)
  testImplementation(libs.mockk)
}
