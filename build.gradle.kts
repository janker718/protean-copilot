plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.17.0"
}

group = "com.protean.copilot"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdea("2025.2.6.1")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
    implementation("com.google.code.gson:gson:2.10.1")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "252"
        }
        changeNotes = """
            Initial Protean Copilot plugin skeleton.
        """.trimIndent()
    }
}

tasks {
    withType<JavaCompile> {
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}