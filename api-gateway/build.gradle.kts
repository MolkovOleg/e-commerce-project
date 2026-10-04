plugins {
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(platform(libs.spring.cloud.dependencies))
    implementation(libs.spring.cloud.starter.gateway)
    implementation(libs.spring.boot.starter.actuator)

    implementation(libs.jjwt.api)
    implementation(libs.jjwt.impl)
    implementation(libs.jjwt.jackson)

    implementation(libs.spring.boot.starter.data.redis.reactive)
}