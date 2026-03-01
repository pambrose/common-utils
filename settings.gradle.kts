pluginManagement {
  resolutionStrategy {
    eachPlugin {
      if (requested.id.namespace == "com.pambrose") {
        useModule("com.github.pambrose:pambrose-gradle-plugins:${requested.version}")
      }
    }
  }

  repositories {
    maven("https://jitpack.io")
    gradlePluginPortal()
  }
}

plugins {
  id("org.gradle.toolchains.foojay-resolver-convention").version("1.0.0")
}

dependencyResolutionManagement {
  repositories {
    mavenCentral()
  }
}

rootProject.name = "common-utils"

include("common-utils-bom")
include("core-utils")
include("dropwizard-utils")
include("email-utils")
include("exposed-utils")
include("grpc-utils")
include("guava-utils")
include("json-utils")
include("jetty-utils")
include("ktor-client-utils")
include("ktor-server-utils")
include("prometheus-utils")
include("recaptcha-utils")
include("redis-utils")
include("script-utils-common")
include("script-utils-python")
include("script-utils-java")
include("script-utils-kotlin")
include("service-utils")
include("zipkin-utils")
