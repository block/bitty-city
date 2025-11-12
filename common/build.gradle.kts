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
    implementation(libs.jodaMoney)

    testImplementation(libs.kotestAssertions)
    testImplementation(libs.kotestJunitRunnerJvm)
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}
