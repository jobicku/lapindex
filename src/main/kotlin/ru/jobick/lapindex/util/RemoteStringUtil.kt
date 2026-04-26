package ru.jobick.lapindex.util

import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList

object RemoteStringUtil {

    fun isRemoteStringKey(expr: KtStringTemplateExpression): Boolean {
        if (expr.entries.any { it !is KtLiteralStringTemplateEntry }) return false
        val arg = expr.parent as? KtValueArgument ?: return false
        val argList = arg.parent as? KtValueArgumentList ?: return false
        val call = argList.parent as? KtCallExpression ?: return false
        return call.calleeExpression?.text == "remoteString"
            && argList.arguments.firstOrNull() == arg
    }

    fun getKeyText(expr: KtStringTemplateExpression): String? {
        if (expr.entries.any { it !is KtLiteralStringTemplateEntry }) return null
        return expr.entries
            .filterIsInstance<KtLiteralStringTemplateEntry>()
            .joinToString("") { it.text }
    }
}
