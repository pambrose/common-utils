plugins {
    `java-platform`
    `maven-publish`
}

// Suppress Gradle Module Metadata — BOMs are a Maven concept and the .module file
// causes JitPack to misidentify this as the root project (com.github.pambrose:common-utils)
// instead of the submodule (com.github.pambrose.common-utils:common-utils-bom)
tasks.withType<GenerateModuleMetadata> {
    enabled = false
}

val versionStr: String by extra

dependencies {
    constraints {
        api("com.github.pambrose.common-utils:core-utils:$versionStr")
        api("com.github.pambrose.common-utils:dropwizard-utils:$versionStr")
        api("com.github.pambrose.common-utils:email-utils:$versionStr")
        api("com.github.pambrose.common-utils:exposed-utils:$versionStr")
        api("com.github.pambrose.common-utils:grpc-utils:$versionStr")
        api("com.github.pambrose.common-utils:guava-utils:$versionStr")
        api("com.github.pambrose.common-utils:json-utils:$versionStr")
        api("com.github.pambrose.common-utils:jetty-utils:$versionStr")
        api("com.github.pambrose.common-utils:ktor-client-utils:$versionStr")
        api("com.github.pambrose.common-utils:ktor-server-utils:$versionStr")
        api("com.github.pambrose.common-utils:prometheus-utils:$versionStr")
        api("com.github.pambrose.common-utils:recaptcha-utils:$versionStr")
        api("com.github.pambrose.common-utils:redis-utils:$versionStr")
        api("com.github.pambrose.common-utils:script-utils-common:$versionStr")
        api("com.github.pambrose.common-utils:script-utils-python:$versionStr")
        api("com.github.pambrose.common-utils:script-utils-java:$versionStr")
        api("com.github.pambrose.common-utils:script-utils-kotlin:$versionStr")
        api("com.github.pambrose.common-utils:service-utils:$versionStr")
        api("com.github.pambrose.common-utils:zipkin-utils:$versionStr")
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["javaPlatform"])
            groupId = project.group.toString()
            artifactId = "common-utils-bom"
            version = versionStr
        }
    }
    repositories {
        maven {
            url = uri("https://jitpack.io")
        }
    }

}
