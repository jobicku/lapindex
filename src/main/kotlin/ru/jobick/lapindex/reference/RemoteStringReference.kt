package ru.jobick.lapindex.reference

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import ru.jobick.lapindex.index.LapindexJsonIndex
import ru.jobick.lapindex.util.RemoteStringUtil

class RemoteStringReference(element: KtStringTemplateExpression) :
    PsiReferenceBase<KtStringTemplateExpression>(element, true) {

    override fun resolve(): PsiElement? {
        val key = RemoteStringUtil.getKeyText(element) ?: return null
        val module = ModuleUtilCore.findModuleForPsiElement(element)
        return LapindexJsonIndex.getInstance(element.project).find(key, module)?.property
    }

    override fun getVariants(): Array<Any> = emptyArray()
}
