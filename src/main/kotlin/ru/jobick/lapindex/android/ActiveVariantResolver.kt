package ru.jobick.lapindex.android

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager

object ActiveVariantResolver {

    // After Gradle sync, IntelliJ registers only the active variant's source roots in the
    // module. We extract source-set names from those paths (e.g. "app/src/dev/java" → "dev")
    // and use them to pick the right JSON file — no Android plugin reflection required.
    fun getActiveSourceSetNames(module: Module): List<String> {
        return try {
            val segments = ModuleRootManager.getInstance(module).sourceRoots
                .mapNotNull { root ->
                    val path = root.path
                    val srcIdx = path.lastIndexOf("/src/")
                    if (srcIdx < 0) return@mapNotNull null
                    val afterSrc = path.substring(srcIdx + 5)
                    afterSrc.substringBefore('/').ifBlank { null }
                }
                .distinct()
            // Put "main" last so variant-specific source sets take priority
            val nonMain = segments.filter { it != "main" }
            val main = segments.filter { it == "main" }
            nonMain + main
        } catch (_: Exception) {
            emptyList()
        }
    }
}
