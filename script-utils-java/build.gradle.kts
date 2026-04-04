description = "Java scripting engine utilities"

dependencies {
    implementation(project(":core-utils"))
    implementation(project(":script-utils-common"))

    implementation(libs.java.scripting)

    testImplementation(libs.kotest)
    testImplementation(kotlin("test"))
}
