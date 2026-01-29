description = "grpc-utils"

dependencies {
    implementation(project(":core-utils"))

    implementation(platform(libs.grpc.bom))
    implementation(libs.grpc.netty)
    implementation(libs.grpc.inprocess)
    implementation(libs.grpc.protobuf)
    implementation(libs.grpc.stub)
    implementation(libs.grpc.services)

    runtimeOnly(libs.netty.ssl)

    testImplementation(libs.kotest)
    testImplementation(kotlin("test"))
}
