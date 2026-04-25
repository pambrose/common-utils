description = "Java scripting engine utilities"

dependencies {
    api(project(":core-utils"))
    api(project(":script-utils-common"))

    implementation(libs.java.scripting)
}
