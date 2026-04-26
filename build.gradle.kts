plugins {
    id("java")
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intellijPlatform)
}

group = "ru.jobick"
version = "1.0.0"

// Set the JVM language level used to build the project.
kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    intellijPlatform {
        val localPath = providers.gradleProperty("platformLocalPath").orNull
            ?.takeIf { File(it).exists() }
        if (localPath != null) {
            local(localPath)
        } else {
            androidStudio(providers.gradleProperty("platformVersion").get())
        }
        bundledPlugin("org.jetbrains.kotlin")
        bundledPlugin("com.intellij.modules.json")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
        }

        changeNotes = """
            1.0.0 - Первая версия. Индексация, навигация, валидация и поддержка build variants
        """.trimIndent()
    }

    instrumentCode = false
}

tasks {
    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }
}
