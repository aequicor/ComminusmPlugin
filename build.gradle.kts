plugins {
    kotlin("jvm") version "2.3.20-RC3"
    id("com.gradleup.shadow") version "8.3.0"
    id("xyz.jpenilla.run-paper") version "2.3.1"
    id("io.gitlab.arturbosch.detekt") version "1.23.7"
}

val isDetekt = gradle.startParameter.taskNames.any { it.contains("detekt", ignoreCase = true) }

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        allWarningsAsErrors.set(project.findProperty("warningsAsErrors")?.toString()?.toBoolean() ?: isDetekt)
    }
}

group = "ru.kyamshanov.minecraft"
version = "1.0-SNAPSHOT"

// Allow overriding Paper API version via Gradle property `papermcApiVersion`.
val papermcApiVersion: String = project.findProperty("papermcApiVersion")?.toString() ?: "1.21.11-R0.1-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.xerial:sqlite-jdbc:3.46.0.0")
    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}

tasks.test {
    useJUnitPlatform()
}

tasks {
    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion("1.21.11")
        downloadPlugins {
            modrinth("P1OZGk5p", "5.8.1")
        }
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.build {
    dependsOn("shadowJar")
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    reports {
        html.required.set(true)
        md.required.set(true)
        sarif.required.set(true)
        txt.required.set(true)
        xml.required.set(true)
    }
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}