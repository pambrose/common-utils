import org.gradle.api.initialization.resolve.RepositoriesMode.FAIL_ON_PROJECT_REPOS

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
    // The Kotlin JS/Wasm toolchain would register its distribution repositories at the
    // project level, which this mode turns into an error; the root build script unsets
    // each toolchain env spec's downloadBaseUrl so those repositories are never added,
    // and the ivy repositories declared below serve the distributions instead.
    repositoriesMode.set(FAIL_ON_PROJECT_REPOS)
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
