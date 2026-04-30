package ru.jobick.lapindex.util

import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList

object RemoteStringUtil {

    fun isRemoteStringKey(expr: KtStringTemplateExpression): Boolean {
        if (expr.entries.any { it !is KtLiteralStringTemplateEntry }) return false
        val arg = expr.parent as? KtValueArgument ?: return false
        val argList = arg.parent as? KtValueArgumentList ?: return false
        if (argList.arguments.firstOrNull() != arg) return false
        val call = argList.parent as? KtCallExpression ?: return false
        return isRemoteStringCall(call) || isDictionaryGetStringCall(call)
    }

    fun getKeyText(expr: KtStringTemplateExpression): String? {
        if (expr.entries.any { it !is KtLiteralStringTemplateEntry }) return null
        return expr.entries
            .filterIsInstance<KtLiteralStringTemplateEntry>()
            .joinToString("") { it.text }
    }

    private fun isRemoteStringCall(call: KtCallExpression): Boolean =
        call.calleeExpression?.text == "remoteString"

    private fun isDictionaryGetStringCall(call: KtCallExpression): Boolean {
        if (call.calleeExpression?.text != "getString") return false
        val qualified = call.parent as? KtDotQualifiedExpression ?: return false
        return qualified.receiverExpression.text == "dictionaryManager"
    }
}
