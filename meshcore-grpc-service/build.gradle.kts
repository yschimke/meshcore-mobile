plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.protobuf)
}

kotlin { jvmToolchain(21) }

dependencies {
  api(projects.meshcoreCore)
  api(libs.grpc.stub)
  api(libs.grpc.protobuf.lite)
  api(libs.grpc.kotlin.stub)
  implementation(libs.protobuf.kotlin.lite)
  implementation(libs.javax.annotation.api)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.io.bytestring)
}

protobuf {
  protoc { artifact = libs.protobuf.protoc.get().toString() }
  plugins {
    create("grpc") { artifact = libs.grpc.protoc.gen.java.get().toString() }
    create("grpckt") { artifact = "${libs.grpc.protoc.gen.kotlin.get()}:jdk8@jar" }
  }
  generateProtoTasks {
    all().forEach { task ->
      task.builtins {
        named("java") { option("lite") }
        create("kotlin") { option("lite") }
      }
      task.plugins {
        create("grpc") { option("lite") }
        create("grpckt") { option("lite") }
      }
    }
  }
}
