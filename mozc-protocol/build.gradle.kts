plugins {
    `java-library`
    id("com.google.protobuf") version "0.9.5"
}

dependencies {
    api("com.google.protobuf:protobuf-javalite:3.25.5")
}

protobuf {
    protoc { artifact = "com.google.protobuf:protoc:3.25.5" }
    generateProtoTasks {
        all().configureEach {
            builtins { named("java") { option("lite") } }
        }
    }
}
