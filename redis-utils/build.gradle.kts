description = "Redis client extension utilities"

dependencies {
    implementation(project(":core-utils"))

    api(libs.redis)
}
