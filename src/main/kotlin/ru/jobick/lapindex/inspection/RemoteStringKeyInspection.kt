package ru.jobick.lapindex.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import ru.jobick.lapindex.index.LapindexJsonIndex
import ru.jobick.lapindex.util.RemoteStringUtil

class RemoteStringKeyInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : KtVisitorVoid() {
            override fun visitStringTemplateExpression(expression: KtStringTemplateExpression) {
                if (!RemoteStringUtil.isRemoteStringKey(expression)) return
                val key = RemoteStringUtil.getKeyText(expression) ?: return
                val module = ModuleUtilCore.findModuleForPsiElement(expression)
                if (LapindexJsonIndex.getInstance(expression.project).find(key, module) == null) {
                    holder.registerProblem(
                        expression,
                        "Unknown remote string key: '$key'",
                        ProblemHighlightType.GENERIC_ERROR
                    )
                }
            }
        }
    }
}
