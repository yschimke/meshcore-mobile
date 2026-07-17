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
      // Make GenerateProtoTask relocatable across machines so it restores from
      // the remote (BuildFetch) build cache. The jar-based grpckt plugin is
      // launched through a `java` trampoline, and the task records the absolute
      // path of that JDK as a cache-key input (`javaExecutablePath`). That path
      // differs between CI and developer/agent machines, so this was the one
      // task that never hit the remote cache. The generated sources don't
      // depend on which JVM runs the plugin, so pin the input to a bare `java`
      // (resolved from PATH at execution time) to keep the key machine-neutral.
      task.javaExecutablePath.set("java")
    }
  }
}
