description = "script-utils-kotlin"

dependencies {
    implementation(project(":core-utils"))
    implementation(project(":script-utils-common"))

    runtimeOnly(libs.kotlin.scripting)

    testImplementation(libs.kotest)
    testImplementation(kotlin("test"))
}
