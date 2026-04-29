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

        val project = params.elementToSearch.project

        // Use the caller-provided scope for filtering; for the word-index lookup we need a
        // GlobalSearchScope — cast it, or fall back to allScope so cross-module files are found.
        // We then re-apply the original (possibly narrower) scope per file so that user-selected
        // scopes such as "Current File" are still honoured.
        val effectiveScope = params.effectiveSearchScope
        val globalScope = ReadAction.compute<GlobalSearchScope, Throwable> {
            (effectiveScope as? GlobalSearchScope) ?: GlobalSearchScope.allScope(project)
        }

        // Keys may contain multiple separator types (. - / :). The word index splits on all of
        // them, so extract the last non-blank word segment to drive the index lookup, then
        // verify the full key inside the file scan.
        val wordHint = key.split('.', '-', '/', ':')
            .lastOrNull { it.isNotBlank() } ?: key

        PsiSearchHelper.getInstance(project).processAllFilesWithWordInLiterals(
            wordHint,
            globalScope,
            { file ->
                // Respect narrower caller-provided scopes (e.g. LocalSearchScope / Current File).
                val vf = file.virtualFile
                if (vf != null && !effectiveScope.contains(vf)) return@processAllFilesWithWordInLiterals true
                if (file.virtualFile?.extension != "kt") return@processAllFilesWithWordInLiterals true
                for (expr in PsiTreeUtil.collectElementsOfType(file, KtStringTemplateExpression::class.java)) {
                    if (!RemoteStringUtil.isRemoteStringKey(expr)) continue
                    if (RemoteStringUtil.getKeyText(expr) != key) continue
                    val ref = expr.references.filterIsInstance<RemoteStringReference>().firstOrNull()
                        ?: continue
                    // Propagate stop signal: if consumer returns false, abort the scan
                    if (!consumer.process(ref)) return@processAllFilesWithWordInLiterals false
                }
                true
            }
        )
    }
}
