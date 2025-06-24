description = "script-utils-common"

dependencies {
    implementation(project(":core-utils"))

    testImplementation(libs.kluent)
    testImplementation(libs.kotlin.test)
}
