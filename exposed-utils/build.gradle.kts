description = "Jetbrains Exposed ORM extension utilities"

dependencies {
    api(project(":core-utils"))

    api(libs.exposed.core)
    api(libs.exposed.jdbc)
    api(libs.exposed.jodatime)
}
