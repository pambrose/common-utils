plugins {
    id("org.jetbrains.kotlin.jvm")
}

description = "grpc-utils"

val versionStr: String by extra

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = group.toString()
            artifactId = project.name
            version = versionStr
            from(components["java"])
        }
    }
}

dependencies {
    implementation(project(":core-utils"))

    implementation(libs.grpc.netty)
    implementation(libs.grpc.inprocess)
    implementation(libs.grpc.protobuf)
    implementation(libs.grpc.stub)
    implementation(libs.grpc.services)

    runtimeOnly(libs.netty.ssl)
}

kotlin {
    jvmToolchain(11)
}
