description = "jetty-utils"

dependencies {
    implementation(project(":core-utils"))

    implementation(libs.jetty.server)
    implementation(libs.jetty.servlet)
}
