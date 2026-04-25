description = "gRPC client and server utilities for Kotlin"

dependencies {
    implementation(project(":core-utils"))

    api(libs.grpc.stub)

    implementation(libs.bundles.grpc)

    runtimeOnly(libs.netty.ssl)
}
