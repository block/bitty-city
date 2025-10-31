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
    implementation(libs.arrowCore)
    implementation(libs.bitcoinj)
    implementation(libs.domainApi)
    implementation(libs.guice)
    implementation(libs.guiceAssistedInject)
    implementation(libs.jodaMoney)
    implementation(libs.kfsm)
    implementation(libs.kotlinLogging)
    implementation(libs.moshi)
    implementation(libs.moshiKotlin)
    implementation(libs.quiverLib)
    implementation(libs.resilience4jKotlin)
    implementation(libs.resilience4jRetry)
    implementation(libs.slf4jApi)
    implementation(libs.slf4jNop)

    testImplementation(libs.flyway)
    testImplementation(libs.flywayMySql)
    testImplementation(libs.kotestProperty)
    testImplementation(libs.kotestAssertions)
    testImplementation(libs.kotestAssertionsArrow)
    testImplementation(libs.kotestAssertionsArrowJvm)
    testImplementation(libs.hikari)
    testImplementation(libs.kotestJunitRunnerJvm)
    testImplementation(libs.mockk)
    testImplementation(libs.mySql)
    testImplementation(libs.mysqlConnectorJava)
    testImplementation(libs.junit4)
    testImplementation(libs.junitPlatformRunner)
    testImplementation(libs.testContainersMySql)
    testImplementation(project(":outie-jooq-provider"))
}
