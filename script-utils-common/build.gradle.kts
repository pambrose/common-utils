description = "script-utils-common"

dependencies {
    implementation(project(":core-utils"))

    testImplementation(libs.kotest)
    testImplementation(kotlin("test"))
}
