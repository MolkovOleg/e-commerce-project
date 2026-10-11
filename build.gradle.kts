import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

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

// Получение каталога версий и ссылку на BOM
val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val springBootBom = catalog.findLibrary("spring-boot-dependencies").get()

subprojects {
    apply(plugin = "java")
    apply(plugin = "checkstyle")
    apply(plugin = "jacoco")

    // Настройка версии Java в проекте
    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    // Настройка checkstyle
    configure<CheckstyleExtension> {
        toolVersion = "10.18.1"
        isIgnoreFailures = false
        maxWarnings = 0
        configFile = rootProject.file("config/checkstyle/checkstyle.xml")
    }

    // Настройка версии JaCoCo
    configure<JacocoPluginExtension> {
        toolVersion = "0.8.12"
    }

    dependencies {

        val bom = platform(springBootBom)

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

    // Запуск unit тестов
    tasks.withType<Test> {
        useJUnitPlatform()
        finalizedBy(tasks.withType<JacocoReport>())
    }

    val jacocoExclusions = listOf(
        "**/dto/**",
        "**/domain/**",
        "**/model/**",
        "**/entity/**",
        "**/config/**",
        "**/*Application*",
        "**/com/ecommerce/common/**"
    )

    tasks.withType<JacocoReport> {
        dependsOn(tasks.withType<Test>())
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
        afterEvaluate {
            classDirectories.setFrom(
                files(classDirectories.files.map {
                    project.fileTree(it) {
                        exclude (jacocoExclusions)
                    }
                })
            )
        }
    }

    tasks.withType<Checkstyle> {
        exclude("**/build/**")
        exclude("**/generated/**")
    }
}