pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "bitty-city"

include(":common")
include(":innie")
include(":outie")

// Include local KFSM and domain-api for development
includeBuild("../kfsm") {
    dependencySubstitution {
        substitute(module("app.cash.kfsm:kfsm")).using(project(":lib"))
    }
}
includeBuild("../domain-api") {
    dependencySubstitution {
        substitute(module("xyz.block.domainapi:domain-api")).using(project(":lib"))
    }
}
