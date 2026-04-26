package ru.jobick.lapindex.android

import com.intellij.openapi.module.Module

object ActiveVariantResolver {

    fun getActiveSourceSetNames(module: Module): List<String> {
        return try {
            val facetClass = Class.forName("com.android.tools.idea.facet.AndroidFacet")
            val facet = facetClass.getMethod("getInstance", Module::class.java)
                .invoke(null, module) ?: return emptyList()
            val modelClass = Class.forName(
                "com.android.tools.idea.gradle.project.model.GradleAndroidModel"
            )
            val model = modelClass.getMethod("get", facetClass)
                .invoke(null, facet) ?: return emptyList()
            val variantName = modelClass.getMethod("getSelectedVariantName")
                .invoke(model) as? String ?: return emptyList()
            decompose(variantName)
        } catch (_: ClassNotFoundException) {
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun decompose(variantName: String): List<String> {
        val parts = variantName.split(Regex("(?=[A-Z])")).map { it.lowercase() }
        return buildList {
            add(variantName)
            addAll(parts)
            add("main")
        }.distinct()
    }
}
