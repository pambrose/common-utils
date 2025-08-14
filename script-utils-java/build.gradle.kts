description = "script-utils-java"

dependencies {
    implementation(project(":core-utils"))
    implementation(project(":script-utils-common"))

    api(libs.java.scripting)

    testImplementation(libs.kotest)
    testImplementation(kotlin("test"))
}
