plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    id("base") // Provides standard task grouping
    id("nu.studer.jooq") version "9.0"
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation(kotlin("stdlib"))
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
    jooqGenerator("com.mysql:mysql-connector-j:8.3.0")
}

// Configure jOOQ code generation
jooq {
    configurations {
        create("main") {  // The name of the jOOQ configuration
            generateSchemaSourceOnCompilation.set(false)  // Disable automatic generation

            jooqConfiguration.apply {
                logging = org.jooq.meta.jaxb.Logging.WARN

                jdbc.apply {
                    driver = "com.mysql.cj.jdbc.Driver"
                    url = "jdbc:mysql://127.0.0.1:3306/bitty_city"
                    user = "root"
                    password = ""
                }

                generator.apply {
                    name = "org.jooq.codegen.KotlinGenerator"

                    database.apply {
                        name = "org.jooq.meta.mysql.MySQLDatabase"
                        inputSchema = "bitty_city"
                        includes = ".*"
                        excludes = "flyway_schema_history"
                    }

                    generate.apply {
                        isPojosAsKotlinDataClasses = true
                        isDeprecated = false
                        isRecords = true
                        isImmutablePojos = true
                        isFluentSetters = true
                    }

                    target.apply {
                        packageName = "xyz.block.bittycity.outie.jooq.generated"
                        directory = "${project.projectDir}/src/main/kotlin"
                    }

                    strategy.name = "org.jooq.codegen.DefaultGeneratorStrategy"
                }
            }
        }
    }
}

// Task to apply migrations to the local MySQL database using Flyway
tasks.register("migrateLocal") {
    group = "database"
    description = "Apply Flyway migrations to the local MySQL database using Flyway Docker image"

    // Ensure MySQL is running before executing the migration
    dependsOn(rootProject.tasks.named("startMysql"))

    doLast {
        val migrationsDir = file("${projectDir}/src/main/resources/migrations")
        logger.lifecycle("Applying Flyway migrations from: ${migrationsDir.absolutePath}")

        // Run Flyway in Docker container
        val process = ProcessBuilder(
            "docker", "run", "--rm",
            "--network=host", // Connect to host network to access MySQL
            "-v", "${migrationsDir.absolutePath}:/flyway/sql",
            "flyway/flyway:latest",
            "-url=jdbc:mysql://127.0.0.1:3306/bitty_city",
            "-user=root",
            "-password=",
            "-validateMigrationNaming=true",
            "-locations=filesystem:/flyway/sql",
            "migrate"
        ).redirectErrorStream(true).start()

        // Capture and log the output
        val output = process.inputStream.bufferedReader().use { it.readText() }
        if (output.isNotEmpty()) {
            logger.lifecycle(output)
        }

        // Check the exit code
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw GradleException("Flyway migration failed with exit code: $exitCode")
        } else {
            logger.lifecycle("Flyway migrations completed successfully!")
        }
    }
}

// Task to view the current migration status and pending migrations
tasks.register("migrationStatus") {
    group = "database"
    description = "Display information about migration status using Flyway Docker image"

    dependsOn(rootProject.tasks.named("startMysql"))

    doLast {
        val migrationsDir = file("${projectDir}/src/main/resources/migrations")
        logger.lifecycle("Checking migration status...")

        // Run Flyway info command in Docker container
        val process = ProcessBuilder(
            "docker", "run", "--rm",
            "--network=host", // Connect to host network to access MySQL
            "-v", "${migrationsDir.absolutePath}:/flyway/sql",
            "flyway/flyway:latest",
            "-url=jdbc:mysql://127.0.0.1:3306/bitty_city",
            "-user=root",
            "-password=",
            "-locations=filesystem:/flyway/sql",
            "info"
        ).redirectErrorStream(true).start()

        // Capture and log the output
        val output = process.inputStream.bufferedReader().use { it.readText() }
        if (output.isNotEmpty()) {
            logger.lifecycle(output)
        }
    }
}

// Task to clean the database (CAUTION: removes all objects in the schema)
tasks.register("cleanDatabase") {
    group = "database"
    description = "CAUTION: Drops all objects in the schema (tables, views, etc.) using Flyway Docker image"

    dependsOn(rootProject.tasks.named("startMysql"))

    doLast {
        val migrationsDir = file("${projectDir}/src/main/resources/migrations")
        logger.lifecycle("WARNING: About to clean the database. This will remove all tables and data!")

        // Run Flyway clean command in Docker container
        val process = ProcessBuilder(
            "docker", "run", "--rm",
            "--network=host", // Connect to host network to access MySQL
            "-v", "${migrationsDir.absolutePath}:/flyway/sql",
            "flyway/flyway:latest",
            "-url=jdbc:mysql://127.0.0.1:3306/bitty_city",
            "-user=root",
            "-password=",
            "-cleanDisabled=false",
            "clean"
        ).redirectErrorStream(true).start()

        // Capture and log the output
        val output = process.inputStream.bufferedReader().use { it.readText() }
        if (output.isNotEmpty()) {
            logger.lifecycle(output)
        }
    }
}

// Task to generate jOOQ classes from the local database
tasks.register("jooqGenerate") {
    group = "database"
    description = "Generate jOOQ classes from the local MySQL database"

    // Depend on migrateLocal to ensure the database schema is up to date
    dependsOn("migrateLocal")

    // This task triggers the built-in generateJooq task provided by the jOOQ plugin
    finalizedBy("generateJooq")

    doLast {
        logger.lifecycle("Generating jOOQ classes from database schema...")
    }
}