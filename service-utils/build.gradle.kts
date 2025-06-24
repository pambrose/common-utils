plugins {
    alias(libs.plugins.kotlin.jvm)
}

description = "service-utils"

val versionStr: String by extra

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = group.toString()
            artifactId = project.name
            version = versionStr
            from(components["java"])
        }
    }
}

dependencies {
    implementation(project(":core-utils"))
    implementation(project(":guava-utils"))
    implementation(project(":jetty-utils"))
    implementation(project(":prometheus-utils"))
    implementation(project(":dropwizard-utils"))
    implementation(project(":zipkin-utils"))

    implementation(libs.guava)
    implementation(libs.prometheus.servlet)
    implementation(libs.prometheus.dropwizard)
    implementation(libs.dropwizard.core)
    implementation(libs.dropwizard.healthcheck)
    implementation(libs.dropwizard.servlets)
    implementation(libs.dropwizard.jmx)
    implementation(libs.jetty.server)
    implementation(libs.jetty.servlet)
    implementation(libs.brave)
    implementation(libs.zipkin.core)
    implementation(libs.zipkin.reporter)
    implementation(libs.zipkin.sender.okhttp)
}

kotlin {
    jvmToolchain(11)
}
