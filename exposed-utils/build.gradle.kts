description = "Jetbrains Exposed ORM extension utilities"

dependencies {
    implementation(project(":core-utils"))

    api(libs.exposed.core)
    api(libs.exposed.jdbc)
    api(libs.exposed.jodatime)
}
