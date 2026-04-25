description = "Kotlin scripting engine utilities"

dependencies {
    api(project(":core-utils"))
    api(project(":script-utils-common"))

    runtimeOnly(libs.kotlin.scripting)
}
