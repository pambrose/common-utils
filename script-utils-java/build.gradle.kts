description = "Java scripting engine utilities"

dependencies {
    // script-utils-common already exports core-utils as `api`.
    api(project(":script-utils-common"))

    // JavaScript.assignIsolation takes java-scriptengine's Isolation enum, so it is part of this
    // module's public API rather than an internal detail.
    api(libs.java.scripting)
}
