import com.google.protobuf.gradle.id

plugins {
    id("java-library")
    alias(libs.plugins.avro)
    alias(libs.plugins.protobuf)
}

val protobufVersion = libs.versions.protobuf.get()
val grpcVersion = libs.versions.grpc.get()

dependencies {
    api(libs.spring.boot.starter.validation)
    api(libs.avro)
    api(libs.spring.kafka)
    api(libs.kafka.avro.serializer) {
        // Исключение старых log4j и slf4j, чтобы избежать конфликтов
        exclude(group = "org.slf4j", module = "slf4j-log4j12")
        exclude(group = "log4j", module = "log4j")
    }

    api(libs.protobuf.java)
    api(libs.grpc.protobuf)
    api(libs.grpc.stub)
    compileOnly(libs.jakarta.annotations.api)
    compileOnly(libs.javax.annotations.api)
}

avro {
    isCreateSetters.set(true)
    fieldVisibility.set("PRIVATE")
    stringType.set("String")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                id("grpc")
            }
        }
    }
}

tasks.withType<Checkstyle> {
    enabled = false
}