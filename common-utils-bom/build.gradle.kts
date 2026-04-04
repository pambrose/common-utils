plugins {
    `java-platform`
    id("com.vanniktech.maven.publish.base")
}

val versionStr: String by extra

dependencies {
    constraints {
        api("com.pambrose.common-utils:core-utils:$versionStr")
        api("com.pambrose.common-utils:dropwizard-utils:$versionStr")
        api("com.pambrose.common-utils:email-utils:$versionStr")
        api("com.pambrose.common-utils:exposed-utils:$versionStr")
        api("com.pambrose.common-utils:grpc-utils:$versionStr")
        api("com.pambrose.common-utils:guava-utils:$versionStr")
        api("com.pambrose.common-utils:json-utils:$versionStr")
        api("com.pambrose.common-utils:jetty-utils:$versionStr")
        api("com.pambrose.common-utils:ktor-client-utils:$versionStr")
        api("com.pambrose.common-utils:ktor-server-utils:$versionStr")
        api("com.pambrose.common-utils:prometheus-utils:$versionStr")
        api("com.pambrose.common-utils:recaptcha-utils:$versionStr")
        api("com.pambrose.common-utils:redis-utils:$versionStr")
        api("com.pambrose.common-utils:script-utils-common:$versionStr")
        api("com.pambrose.common-utils:script-utils-python:$versionStr")
        api("com.pambrose.common-utils:script-utils-java:$versionStr")
        api("com.pambrose.common-utils:script-utils-kotlin:$versionStr")
        api("com.pambrose.common-utils:service-utils:$versionStr")
        api("com.pambrose.common-utils:zipkin-utils:$versionStr")
    }
}

mavenPublishing {
    configure(
        com.vanniktech.maven.publish.JavaPlatform(),
    )
    coordinates("com.pambrose.common-utils", "common-utils-bom", versionStr)

    pom {
        name.set("common-utils-bom")
        description.set("Bill of Materials for common-utils modules")
        url.set("https://github.com/pambrose/common-utils")
        licenses {
            license {
                name.set("Apache License 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0")
            }
        }
        developers {
            developer {
                id.set("pambrose")
                name.set("Paul Ambrose")
                email.set("paul@pambrose.com")
            }
        }
        scm {
            connection.set("scm:git:git://github.com/pambrose/common-utils.git")
            developerConnection.set("scm:git:ssh://github.com/pambrose/common-utils.git")
            url.set("https://github.com/pambrose/common-utils")
        }
    }

    publishToMavenCentral(automaticRelease = true)
    signAllPublications()
}

tasks.withType<Sign>().configureEach {
    isEnabled = project.findProperty("signingInMemoryKey") != null
}
