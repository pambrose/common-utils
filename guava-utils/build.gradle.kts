description = "Google Guava extension utilities"

dependencies {
    implementation(project(":core-utils"))

    api(libs.guava)

    testImplementation(libs.kotest)
    testImplementation(kotlin("test"))
}
