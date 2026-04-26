package ru.jobick.lapindex.findusages

import com.intellij.json.psi.JsonProperty
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import ru.jobick.lapindex.reference.RemoteStringReference
import ru.jobick.lapindex.util.RemoteStringUtil

class JsonPropertyUsagesSearcher : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(false) {

    override fun processQuery(
        params: ReferencesSearch.SearchParameters,
        consumer: Processor<in PsiReference>
    ) {
        val key = ReadAction.compute<String?, Throwable> {
            (params.elementToSearch as? JsonProperty)?.name
        } ?: return

        val scope = ReadAction.compute<GlobalSearchScope?, Throwable> {
            params.effectiveSearchScope as? GlobalSearchScope
        } ?: return

        val project = params.elementToSearch.project

        // Dots in keys split into words by the index, so use the last segment
        // to locate candidate files, then verify the full key.
        val wordHint = key.split('.').lastOrNull { it.isNotBlank() } ?: key

        PsiSearchHelper.getInstance(project).processAllFilesWithWordInLiterals(
            wordHint,
            scope,
            { file ->
                if (file.virtualFile?.extension != "kt") return@processAllFilesWithWordInLiterals true
                PsiTreeUtil.collectElementsOfType(file, KtStringTemplateExpression::class.java)
                    .forEach { expr ->
                        if (!RemoteStringUtil.isRemoteStringKey(expr)) return@forEach
                        if (RemoteStringUtil.getKeyText(expr) != key) return@forEach
                        expr.references.filterIsInstance<RemoteStringReference>()
                            .firstOrNull()
                            ?.let { consumer.process(it) }
                    }
                true
            }
        )
    }
}
