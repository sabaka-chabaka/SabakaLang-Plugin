package com.sabakachabaka.sabakalang

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.DocumentationMarkup.*
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.sabakachabaka.sabakalang.psi.*

class SabakaDocumentationProvider : AbstractDocumentationProvider() {

    override fun generateDoc(element: PsiElement, originalElement: PsiElement?): String? {
        val name = (element as? SabakaNamedElement)?.name ?: return null

        SabakaBuiltins.GLOBAL_BY_NAME[name]?.let { bi ->
            return buildString {
                append(DEFINITION_START)
                append("<b>built-in</b> <code>${esc(bi.signature)}</code>")
                append(DEFINITION_END)
                append(CONTENT_START)
                append(bi.description)
                append(CONTENT_END)
                if (bi.params.isNotEmpty()) {
                    append(SECTIONS_START)
                    append(SECTION_HEADER_START).append("Parameters").append(SECTION_SEPARATOR)
                    bi.params.forEach { (n, t) -> append("<p><code>$n</code> : <i>$t</i>") }
                    append(SECTIONS_END)
                }
                append(SECTIONS_START)
                append(SECTION_HEADER_START).append("Returns").append(SECTION_SEPARATOR)
                append("<code>${bi.returnType}</code>")
                append(SECTIONS_END)
            }
        }

        return when (element) {
            is SabakaFuncDecl -> buildString {
                append(DEFINITION_START)
                append("<b>${esc(element.name ?: "?")}</b>(...)")
                append(DEFINITION_END)
                append(CONTENT_START)
                val params = PsiTreeUtil.findChildrenOfType(element.getParamList(), SabakaParam::class.java)
                if (params.isNotEmpty()) {
                    append("<b>Parameters:</b><br>")
                    params.forEach { p -> append("&nbsp;&nbsp;<code>${p.name}</code><br>") }
                }
                append(CONTENT_END)
            }
            is SabakaClassDecl -> buildString {
                append(DEFINITION_START).append("<b>class</b> <b>${esc(element.name ?: "?")}</b>").append(DEFINITION_END)
                append(CONTENT_START)
                val body = element.getBody()
                val fields = body?.let { PsiTreeUtil.getChildrenOfType(it, SabakaFieldDecl::class.java) }
                if (!fields.isNullOrEmpty()) {
                    append("<b>Fields:</b> ").append(fields.mapNotNull { it.name }.joinToString(", ")).append("<br>")
                }
                val methods = body?.let { PsiTreeUtil.getChildrenOfType(it, SabakaFuncDecl::class.java) }
                if (!methods.isNullOrEmpty()) {
                    append("<b>Methods:</b> ").append(methods.mapNotNull { it.name }.joinToString(", "))
                }
                append(CONTENT_END)
            }
            is SabakaStructDecl -> buildSimple("struct", element.name)
            is SabakaEnumDecl   -> buildSimple("enum", element.name)
            is SabakaParam      -> buildSimple("parameter", element.name)
            is SabakaVarDeclStmt -> buildSimple("variable", element.name)
            is SabakaFieldDecl  -> buildSimple("field", element.name)
            else -> null
        }
    }

    override fun getQuickNavigateInfo(element: PsiElement, originalElement: PsiElement?): String? {
        val name = (element as? SabakaNamedElement)?.name ?: return null
        SabakaBuiltins.GLOBAL_BY_NAME[name]?.let { return "built-in ${it.signature}" }
        return when (element) {
            is SabakaFuncDecl   -> "${element.name}(...)"
            is SabakaClassDecl  -> "class ${element.name}"
            is SabakaStructDecl -> "struct ${element.name}"
            is SabakaEnumDecl   -> "enum ${element.name}"
            is SabakaParam      -> "param ${element.name}"
            else -> null
        }
    }

    private fun buildSimple(kind: String, name: String?) = buildString {
        append(DEFINITION_START).append("<b>$kind</b> <code>${esc(name ?: "?")}</code>").append(DEFINITION_END)
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
