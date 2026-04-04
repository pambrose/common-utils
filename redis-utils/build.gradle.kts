description = "Redis client extension utilities"

dependencies {
    implementation(project(":core-utils"))

    implementation(libs.redis)

    testImplementation(libs.kotest)
    testImplementation(kotlin("test"))
}
