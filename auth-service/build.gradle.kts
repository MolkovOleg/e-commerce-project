plugins {
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":common-lib"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)

    implementation(libs.spring.boot.starter.security)
    implementation(libs.jjwt.api)
    implementation(libs.jjwt.impl)
    implementation(libs.jjwt.jackson)

    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)

    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.springdoc.openapi.starter.webmvc.ui)
}