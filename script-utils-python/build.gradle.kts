description = "Python scripting integration utilities"

dependencies {
    api(project(":core-utils"))
    api(project(":script-utils-common"))

    implementation(libs.python.scripting)
}
