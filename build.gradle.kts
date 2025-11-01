repositories {
  mavenCentral()
  google()
}

plugins {
  alias(libs.plugins.kotlinGradlePlugin) apply false
  alias(libs.plugins.dokka)
  alias(libs.plugins.versionsGradlePlugin)
  alias(libs.plugins.versionCatalogUpdateGradlePlugin)
  alias(libs.plugins.kotlinBinaryCompatibilityPlugin) apply false
  id("com.vanniktech.maven.publish") version "0.34.0" apply false
}

subprojects {
  apply(plugin = "org.jetbrains.dokka")

  // Configure Kotlin compilation target for modules applying Kotlin
  tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
  }

  tasks.withType<Test> {
    useJUnitPlatform()
  }

  dokka {
    moduleName.set(project.name)
    dokkaPublications.html {
      suppressInheritedMembers.set(true)
      failOnWarning.set(true)
    }
    dokkaSourceSets.configureEach {
      includes.from("module.md")
      sourceLink {
        localDirectory.set(file("src/main/kotlin"))
        remoteUrl.set(uri("https://github.com/block/bitty-city/tree/main/${project.name}/src/main/kotlin"))
        remoteLineSuffix.set("#L")
      }
    }
  }
}

// Configure Dokka multi-module task
dokka {
  moduleName.set("bitty-city")
  moduleVersion.set(project.version.toString())
  dokkaPublications {
    register("multiModule") {
      outputDirectory.set(layout.buildDirectory.dir("dokka/html"))
      includes.from("dokka-docs/module.md")
    }
  }
}

tasks.register("publishToMavenCentral") {
  group = "publishing"
  dependsOn(
//    ":innie:publishToMavenCentral",
    ":outie:publishToMavenCentral",
    ":outie-jooq-provider:publishToMavenCentral"
  )
}
