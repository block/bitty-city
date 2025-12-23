import org.flywaydb.core.Flyway
import org.jooq.codegen.GenerationTool
import org.jooq.meta.jaxb.*
import org.testcontainers.mysql.MySQLContainer
import org.testcontainers.utility.DockerImageName

plugins {
  id("java-library")
  id("org.jetbrains.kotlin.jvm")
  id("base") // Provides standard task grouping
  id("nu.studer.jooq") version "9.0"
  id("org.flywaydb.flyway") version "11.20.0"
  id("com.vanniktech.maven.publish")
}

buildscript {
  repositories {
    mavenCentral()
  }
  dependencies {
    classpath(libs.flyway)
    classpath(libs.flywayMySql)
    classpath(libs.jooqCodegen)
    classpath(libs.jooqKotlin)
    classpath(libs.mysqlConnectorJava)
    classpath(platform(libs.testContainersBom))
    classpath(libs.testContainers)
    classpath(libs.testContainersMySql)
  }
}

repositories {
    mavenCentral()
}

mavenPublishing {
  configure(com.vanniktech.maven.publish.KotlinJvm())
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

val jooqOut = layout.buildDirectory.dir("generated-src/jooq")
sourceSets {
  named("main") {
      kotlin.srcDir(jooqOut)
  }
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":common"))
    implementation(project(":outie"))
    implementation(libs.arrowCore)
    implementation(libs.bitcoinj)
    implementation(libs.domainApi)
    implementation(libs.jodaMoney)
    implementation(libs.kfsm)
    implementation(libs.moshi)
    implementation(libs.moshiKotlin)
    implementation(libs.quiverLib)

    api(libs.jooq)
    api(libs.jooqKotlin)
    api(libs.jooqKotlinCoroutines)

    // JOOQ code generation dependencies
    jooqGenerator(libs.mysqlConnectorJava)
}

tasks.register("generateJooq") {
  group = "code generation"
  description = "Start MySQL via Testcontainers, run Flyway, generate jOOQ sources."

  dependsOn("processResources")

  inputs.files(fileTree("src/main/resources/db/migration"))
  outputs.dir(jooqOut)

  doLast {
    val image = DockerImageName.parse("mysql:8.4")
    val mysql = MySQLContainer(image)
      .withDatabaseName("app")
      .withUsername("root")
      .withPassword("password")

    logger.lifecycle("Starting MySQL Testcontainer…")
    mysql.start()

    try {
      val jdbc = mysql.jdbcUrl
      val user = mysql.username
      val pass = mysql.password

      logger.lifecycle("Running Flyway migrations…")
      Flyway.configure()
        .dataSource(jdbc, user, pass)
        .locations("filesystem:${project.projectDir}/src/main/resources/db/migration")
        .baselineOnMigrate(true)
        .load()
        .migrate()

      val outDir = jooqOut.get().asFile.apply { mkdirs() }

      logger.lifecycle("Generating jOOQ…")
      GenerationTool.generate(
        Configuration()
          .withJdbc(Jdbc()
            .withDriver("com.mysql.cj.jdbc.Driver")
            .withUrl(jdbc)
            .withUser(user)
            .withPassword(pass)
          )
          .withGenerator(Generator()
            .withName("org.jooq.codegen.KotlinGenerator")
            .withDatabase(
              Database()
                .withName("org.jooq.meta.mysql.MySQLDatabase")
                .withInputSchema("app")
                .withExcludes("flyway_schema_history")
            )
            .withGenerate(
              Generate()
                .withPojosAsKotlinDataClasses(true)
                .withKotlinNotNullPojoAttributes(true)
                .withKotlinNotNullRecordAttributes(true)
                .withKotlinDefaultedNullablePojoAttributes(true)
                .withKotlinDefaultedNullableRecordAttributes(true)
                .withKotlinSetterJvmNameAnnotationsOnIsPrefix(true)
                .withImplicitJoinPathsAsKotlinProperties(true)
            )
            .withTarget(
              org.jooq.meta.jaxb.Target()
                .withDirectory(outDir.absolutePath)
                .withPackageName("xyz.block.bittycity.outie.jooq.generated")
            )
          )
      )
      logger.lifecycle("jOOQ generated into ${outDir.absolutePath}")
    } finally {
      mysql.stop()
    }
  }
}

// Make compileKotlin depend on generateJooq to ensure code is generated before compilation
tasks.named("compileKotlin") {
    dependsOn("generateJooq")
}

// Make sourcesJar depend on generateJooq since it includes generated sources
tasks.named("sourcesJar") {
    dependsOn("generateJooq")
}
