package ru.jobick.lapindex.navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import ru.jobick.lapindex.util.RemoteStringUtil

class JsonPropertyGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor
    ): Array<PsiElement>? {
        val literal = sourceElement?.parent as? JsonStringLiteral ?: return null
        val property = literal.parent as? JsonProperty ?: return null
        if (property.nameElement != literal) return null

        val key = property.name
        val project = sourceElement.project
        val wordHint = key.split('.', '-', '/', ':').lastOrNull { it.isNotBlank() } ?: key
        val scope = GlobalSearchScope.projectScope(project)

        val results = mutableListOf<PsiElement>()
        PsiSearchHelper.getInstance(project).processAllFilesWithWordInLiterals(wordHint, scope) { file ->
            if (file.virtualFile?.extension == "kt") {
                for (expr in PsiTreeUtil.collectElementsOfType(file, KtStringTemplateExpression::class.java)) {
                    if (!RemoteStringUtil.isRemoteStringKey(expr)) continue
                    if (RemoteStringUtil.getKeyText(expr) != key) continue
                    results.add(expr)
                }
            }
            true
        }
        return if (results.isEmpty()) null else results.toTypedArray()
    }
}
