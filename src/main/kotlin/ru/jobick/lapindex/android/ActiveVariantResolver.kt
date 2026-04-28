package ru.jobick.lapindex.android

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleRootManager

object ActiveVariantResolver {

    // After Gradle sync, IntelliJ registers only the active variant's source roots.
    // In multi-module projects the Kotlin call site is often in a feature module that only has
    // "main", while the variant-specific roots (dev/prod) live in a different module (e.g. lapi:impl).
    // Scanning all project modules ensures we always detect the active build variant.
    fun getActiveSourceSetNames(module: Module): List<String> {
        return try {
            val allModules = ModuleManager.getInstance(module.project).modules
            val segments = allModules.flatMap { m ->
                ModuleRootManager.getInstance(m).sourceRoots
                    .mapNotNull { root ->
                        val path = root.path
                        val srcIdx = path.lastIndexOf("/src/")
                        if (srcIdx < 0) return@mapNotNull null
                        val afterSrc = path.substring(srcIdx + 5)
                        afterSrc.substringBefore('/').ifBlank { null }
                    }
            }.distinct()
            // Put "main" last so variant-specific source sets take priority
            val nonMain = segments.filter { it != "main" }
            val main = segments.filter { it == "main" }
            nonMain + main
        } catch (_: Exception) {
            emptyList()
        }
    }
}
