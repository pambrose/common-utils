description = "gRPC client and server utilities for Kotlin"

dependencies {
    api(project(":core-utils"))

    api(libs.grpc.stub)
    // TlsContext.sslContext and TlsContextBuilder.builder are Netty types (SslContext,
    // SslContextBuilder) that arrive through grpc-netty, so consumers need it on their compile classpath.
    api(libs.grpc.netty)

    implementation(libs.bundles.grpc)

    runtimeOnly(libs.netty.tcnative)
    // The main tcnative jar holds no native code. Its POM adds the per-platform jars as classifier dependencies on
    // itself, which Gradle drops, so without these OpenSSL never loads and Netty falls back to the JDK TLS provider.
    // The classifiers are the ones that POM lists.
    listOf("linux-x86_64", "linux-aarch_64", "osx-x86_64", "osx-aarch_64", "windows-x86_64").forEach { platform ->
        runtimeOnly(variantOf(libs.netty.tcnative) { classifier(platform) })
    }

    testImplementation(libs.mockk)
}
