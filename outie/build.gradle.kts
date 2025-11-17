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
    implementation(libs.moshi)
    implementation(libs.moshiKotlin)
    implementation(libs.quiverLib)
    implementation(libs.resilience4jKotlin)
    implementation(libs.resilience4jRetry)
    implementation(libs.slf4jApi)
    implementation(libs.slf4jNop)

    testImplementation(testFixtures(project(":common")))
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

tasks.register<JavaExec>("generateStateMachineDiagram") {
    group = "documentation"
    description = "Generate state machine diagram in Mermaid format"
    
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("xyz.block.bittycity.outie.fsm.StateMachineDiagramGeneratorKt")
    
    val outputFile = project.file("docs/state-machine.md")
    args(outputFile.absolutePath)
    
    // Track FSM package files as inputs for incremental builds
    inputs.files(fileTree("src/main/kotlin/xyz/block/bittycity/outie/fsm") {
        include("**/*.kt")
    })
    inputs.file("src/test/kotlin/xyz/block/bittycity/outie/fsm/StateMachineDiagramGenerator.kt")
    
    // Declare output for incremental builds
    outputs.file(outputFile)
}

tasks.named("assemble") {
    dependsOn("generateStateMachineDiagram")
}
