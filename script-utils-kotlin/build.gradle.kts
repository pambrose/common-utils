description = "script-utils-kotlin"

dependencies {
    implementation(project(":core-utils"))
    implementation(project(":script-utils-common"))

    runtimeOnly(libs.kotlin.scripting)

    testImplementation(libs.kluent)
    testImplementation(kotlin("test"))
}
