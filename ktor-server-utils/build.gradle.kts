plugins {
    `java-library`
}

description = "Ktor server framework extension utilities"

dependencies {
    // Declared directly rather than inherited: this module used none of core-utils' own code, but reached
    // kotlin-logging through core-utils' `api` export. Dropping core-utils without this breaks the build.
    implementation(libs.kotlin.logging)

    api(libs.ktor.server.core)

    compileOnlyApi(libs.jakarta.servlet.api)

    testImplementation(libs.jakarta.servlet.api)
    testImplementation(libs.ktor.server.cio)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.mockk)
}

// compileOnlyApi keeps the servlet API off Gradle consumers' runtime classpath, but the POM lists it at compile
// scope, so Maven consumers would get it at runtime (or bundled into a WAR). Mark it optional there too.
plugins.withId("maven-publish") {
    extensions.configure<PublishingExtension> {
        publications.withType<MavenPublication>().configureEach {
            pom.withXml {
                val dependencies = asElement().getElementsByTagName("dependency")
                for (i in 0 until dependencies.length) {
                    val dependency = dependencies.item(i) as org.w3c.dom.Element
                    val artifactId = dependency.getElementsByTagName("artifactId").item(0).textContent
                    if (artifactId == "jakarta.servlet-api") {
                        dependency.appendChild(
                            dependency.ownerDocument.createElement("optional").apply { textContent = "true" },
                        )
                    }
                }
            }
        }
    }
}
