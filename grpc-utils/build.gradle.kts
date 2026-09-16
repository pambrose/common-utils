description = "gRPC client and server utilities for Kotlin"

dependencies {
    api(project(":core-utils"))

    api(libs.grpc.stub)
    // TlsContext.sslContext and TlsContextBuilder.builder are Netty types (SslContext,
    // SslContextBuilder) that arrive through grpc-netty, so consumers need it on their compile classpath.
    api(libs.grpc.netty)

    implementation(libs.bundles.grpc)

    runtimeOnly(libs.netty.tcnative)

    testImplementation(libs.mockk)
}
