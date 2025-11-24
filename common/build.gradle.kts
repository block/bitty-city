plugins {
  id("java-library")
  id("java-test-fixtures")
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
    implementation(libs.arrowCore)
    implementation(libs.domainApi)
    implementation(libs.jodaMoney)
    implementation(libs.kfsm)
    implementation(libs.moshi)
    implementation(libs.quiverLib)
    implementation(libs.resilience4jKotlin)
    implementation(libs.resilience4jRetry)

    testImplementation(libs.kotestAssertions)
    testImplementation(libs.kotestJunitRunnerJvm)
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}
