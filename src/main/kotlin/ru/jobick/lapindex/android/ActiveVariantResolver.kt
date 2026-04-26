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
            val model = resolveModel(modelClass, facetClass, facet) ?: return emptyList()
            val variantName = modelClass.getMethod("getSelectedVariantName")
                .invoke(model) as? String ?: return emptyList()
            decompose(variantName)
        } catch (_: ClassNotFoundException) {
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    // GradleAndroidModel.get() may be a Kotlin companion function without @JvmStatic,
    // so invoking it as a static method fails with NoSuchMethodException. Fall back to
    // accessing the companion object field and invoking through it.
    private fun resolveModel(modelClass: Class<*>, facetClass: Class<*>, facet: Any): Any? {
        return try {
            modelClass.getMethod("get", facetClass).invoke(null, facet)
        } catch (_: NoSuchMethodException) {
            try {
                val companionField = modelClass.getDeclaredField("Companion")
                companionField.isAccessible = true
                val companion = companionField.get(null)
                companion.javaClass.getMethod("get", facetClass).invoke(companion, facet)
            } catch (_: Exception) {
                null
            }
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
