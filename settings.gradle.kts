import org.gradle.api.initialization.resolve.RepositoriesMode.PREFER_SETTINGS

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention").version("1.0.0")
}

dependencyResolutionManagement {
    // PREFER_SETTINGS rather than FAIL_ON_PROJECT_REPOS: the Kotlin JS/Wasm toolchain
    // registers its distribution repositories at the project level, which the FAIL
    // mode turns into an error. PREFER_SETTINGS ignores those and resolves everything
    // from the repositories declared here.
    repositoriesMode.set(PREFER_SETTINGS)
    repositories {
        mavenCentral()

        // The Kotlin JS/Wasm toolchain downloads its Node.js, Yarn, and Binaryen
        // distributions through these ivy repositories.
        exclusiveContent {
            forRepository {
                ivy("https://nodejs.org/dist") {
                    name = "Node Distributions"
                    patternLayout { artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]") }
                    metadataSources { artifact() }
                }
            }
            filter { includeModule("org.nodejs", "node") }
        }
        exclusiveContent {
            forRepository {
                ivy("https://github.com/yarnpkg/yarn/releases/download") {
                    name = "Yarn Distributions"
                    patternLayout { artifact("v[revision]/[artifact](-v[revision]).[ext]") }
                    metadataSources { artifact() }
                }
            }
            filter { includeModule("com.yarnpkg", "yarn") }
        }
        exclusiveContent {
            forRepository {
                ivy("https://github.com/WebAssembly/binaryen/releases/download") {
                    name = "Binaryen Distributions"
                    patternLayout { artifact("version_[revision]/[module]-version_[revision]-[classifier].[ext]") }
                    metadataSources { artifact() }
                }
            }
            filter { includeModule("com.github.webassembly", "binaryen") }
        }
    }
}

rootProject.name = "common-utils"

include("core-utils")
include("dropwizard-utils")
include("email-utils")
include("exposed-utils")
include("grpc-utils")
include("guava-utils")
include("jetty-utils")
include("json-utils")
include("ktor-client-utils")
include("ktor-server-utils")
include("prometheus-utils")
include("recaptcha-utils")
include("redis-utils")
include("script-utils-common")
include("script-utils-java")
include("script-utils-kotlin")
include("script-utils-python")
include("service-utils")
include("zipkin-utils")
