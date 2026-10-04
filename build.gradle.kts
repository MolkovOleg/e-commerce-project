plugins {
    java
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.avro) apply false
}

allprojects {
    group = "com.ecommerce"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()

        // Добавление репозитория Confluent для Avro сериализации и Schema Registry клиентов
        maven {
            url = uri("https://packages.confluent.io/maven/")
        }
    }
}

subprojects {
    apply(plugin = "java")

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    dependencies {
        val bom = platform("org.springframework.boot:spring-boot-dependencies:3.3.4")

        // Подключение BOM
        implementation(bom)
        annotationProcessor(bom)
        testAnnotationProcessor(bom)

        // Lombok
        compileOnly("org.projectlombok:lombok")
        annotationProcessor("org.projectlombok:lombok")
        testCompileOnly("org.projectlombok:lombok")
        testAnnotationProcessor("org.projectlombok:lombok")

        // Тесты
        testImplementation("org.springframework.boot:spring-boot-starter-test")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}