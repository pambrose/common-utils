description = "guava-utils"

dependencies {
    implementation(project(":core-utils"))

    implementation(libs.guava)

    testImplementation(libs.kluent)
    testImplementation(kotlin("test"))
}
