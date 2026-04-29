plugins {
    id("java")
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intellijPlatform)
}

group = "ru.jobick"
version = "1.1.0"

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
            <b>1.1.0</b>
            <ul>
              <li>Исправлена навигация из JSON-словаря в Kotlin-файлы (Find Usages в обратную сторону) — #1, #4</li>
              <li>Исправлен выбор словаря по активному build variant: теперь учитывается вариант модуля-владельца файла, а не вызывающего кода — #2, #5</li>
              <li>Исправлена поддержка многомодульных проектов: Find Usages из JSON находит вызовы remoteString() в feature-модулях — #4</li>
              <li>Исправлен приоритет source set при выборе variant-файла: порядок из ActiveVariantResolver теперь важнее порядка файлов в настройках</li>
              <li>Исправлено: Find Usages теперь уважает выбранный пользователем scope (например, "Current File")</li>
            </ul>
            <b>1.0.0</b>
            <ul>
              <li>Первая версия. Индексация, навигация, валидация и поддержка build variants</li>
            </ul>
        """.trimIndent()
    }

    instrumentCode = false
}

tasks {
    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }
}
