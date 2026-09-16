description = "Python scripting integration utilities"

dependencies {
    // script-utils-common already exports core-utils as `api`.
    api(project(":script-utils-common"))

    implementation(libs.python.scripting)
}
