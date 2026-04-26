package ru.jobick.lapindex.reference

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.util.ProcessingContext
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import ru.jobick.lapindex.util.RemoteStringUtil

class RemoteStringReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(KtStringTemplateExpression::class.java),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(
                    element: PsiElement,
                    context: ProcessingContext
                ): Array<PsiReference> {
                    val expr = element as? KtStringTemplateExpression
                        ?: return PsiReference.EMPTY_ARRAY
                    if (!RemoteStringUtil.isRemoteStringKey(expr)) return PsiReference.EMPTY_ARRAY
                    return arrayOf(RemoteStringReference(expr))
                }
            },
            PsiReferenceRegistrar.LOWER_PRIORITY
        )
    }
}
