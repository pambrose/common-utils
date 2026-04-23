description = "gRPC client and server utilities for Kotlin"

dependencies {
    implementation(project(":core-utils"))

    api(libs.grpc.stub)

    implementation(libs.grpc.netty)
    implementation(libs.grpc.inprocess)
    implementation(libs.grpc.protobuf)
    implementation(libs.grpc.services)

    runtimeOnly(libs.netty.ssl)
}
