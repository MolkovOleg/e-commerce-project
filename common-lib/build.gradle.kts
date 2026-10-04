plugins {
    id("java-library")
    alias(libs.plugins.avro)
}

dependencies {
    api(libs.spring.boot.starter.validation)
    api(libs.avro)
    api(libs.spring.kafka)
    api(libs.kafka.avro.serializer) {
        // Исключение старых log4j и slf4j, чтобы избежать конфликтов
        exclude(group = "org.slf4j", module = "slf4j-log4j12")
        exclude(group = "log4j", module = "log4j")
    }
}

avro {
    isCreateSetters.set(true)
    fieldVisibility.set("PRIVATE")
    stringType.set("String")
}