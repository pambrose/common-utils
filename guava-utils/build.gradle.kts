description = "guava-utils"

dependencies {
    implementation(project(":core-utils"))

    implementation(libs.guava)

    testImplementation(libs.kotest)
    testImplementation(kotlin("test"))
}
