plugins {
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":common-lib"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.springdoc.openapi.starter.webmvc.ui)
}