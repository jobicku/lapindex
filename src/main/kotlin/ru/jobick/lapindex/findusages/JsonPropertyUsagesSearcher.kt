package ru.jobick.lapindex.findusages

import com.intellij.json.psi.JsonProperty
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
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

        // Always search the whole project: the JSON file may live in a low-level impl module
        // (e.g. lapi:impl) whose getUseScope() excludes feature modules that depend only on
        // lapi:api. Using projectScope avoids that incorrect narrowing.
        val searchScope = ReadAction.compute<GlobalSearchScope, Throwable> {
            GlobalSearchScope.projectScope(project)
        }

        // Keys may contain multiple separator types (. - / :). The word index splits on all of
        // them, so extract the last non-blank word segment to drive the index lookup, then
        // verify the full key inside the file scan.
        val wordHint = key.split('.', '-', '/', ':')
            .lastOrNull { it.isNotBlank() } ?: key

        PsiSearchHelper.getInstance(project).processAllFilesWithWordInLiterals(
            wordHint,
            searchScope,
            { file ->
                if (file.virtualFile?.extension != "kt") return@processAllFilesWithWordInLiterals true
                for (expr in PsiTreeUtil.collectElementsOfType(file, KtStringTemplateExpression::class.java)) {
                    if (!RemoteStringUtil.isRemoteStringKey(expr)) continue
                    if (RemoteStringUtil.getKeyText(expr) != key) continue
                    // Synthetic reference that always resolves to the searched JsonProperty.
                    // Using the existing RemoteStringReference would fail isReferenceTo() when
                    // the active build variant resolves to a different JSON file than the one
                    // the user invoked Find Usages on.
                    val ref = object : PsiReferenceBase<KtStringTemplateExpression>(expr, true) {
                        override fun resolve(): PsiElement = params.elementToSearch
                        override fun getVariants(): Array<Any> = emptyArray()
                    }
                    if (!consumer.process(ref)) return@processAllFilesWithWordInLiterals false
                }
                true
            }
        )
    }
}
