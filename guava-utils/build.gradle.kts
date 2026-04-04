description = "Google Guava extension utilities"

dependencies {
    implementation(project(":core-utils"))

    implementation(libs.guava)

    testImplementation(libs.kotest)
    testImplementation(kotlin("test"))
}
