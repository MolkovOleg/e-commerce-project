plugins {
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":common-lib"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.springdoc.openapi.starter.webmvc.ui)

    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)

    implementation(libs.grpc.client.spring.boot.starter)

    implementation(libs.spring.kafka)
    implementation(libs.avro)
    implementation(libs.kafka.avro.serializer) {
        exclude(group = "org.slf4j", module = "slf4j-log4j12")
        exclude(group = "log4j", module = "log4j")
    }

    testImplementation(libs.spring.boot.starter.test)
}