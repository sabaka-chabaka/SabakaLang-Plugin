package com.sabakachabaka.sabakalang

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.sabakachabaka.sabakalang.lexer.SabakaTokenTypes
import com.sabakachabaka.sabakalang.psi.*

class SabakaReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(SabakaVarExpr::class.java),
            SabakaVarRefProvider()
        )
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(SabakaCompositeElement::class.java),
            SabakaCallRefProvider()
        )
    }
}

class SabakaVarRefProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(el: PsiElement, ctx: ProcessingContext): Array<PsiReference> {
        if (el !is SabakaVarExpr) return PsiReference.EMPTY_ARRAY
        return arrayOf(SabakaSymbolRef(el, TextRange(0, el.textLength), el.name ?: return PsiReference.EMPTY_ARRAY))
    }
}

class SabakaCallRefProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(el: PsiElement, ctx: ProcessingContext): Array<PsiReference> {
        if (el !is SabakaCompositeElement) return PsiReference.EMPTY_ARRAY
        if (el.node.elementType != SabakaElementTypes.CALL_EXPR) return PsiReference.EMPTY_ARRAY
        val nameNode = el.node.firstChildNode ?: return PsiReference.EMPTY_ARRAY
        if (nameNode.elementType != SabakaTokenTypes.IDENTIFIER) return PsiReference.EMPTY_ARRAY
        return arrayOf(SabakaSymbolRef(el, TextRange(0, nameNode.textLength), nameNode.text))
    }
}

class SabakaSymbolRef(
    element: PsiElement,
    range: TextRange,
    private val name: String
) : PsiReferenceBase<PsiElement>(element, range) {

    override fun resolve(): PsiElement? {
        val file = element.containingFile as? SabakaFile ?: return null

        // Functions
        PsiTreeUtil.findChildrenOfType(file, SabakaFuncDecl::class.java)
            .firstOrNull { it.name == name }?.let { return it }

        // Classes / structs / enums / interfaces
        PsiTreeUtil.findChildrenOfType(file, SabakaClassDecl::class.java)
            .firstOrNull { it.name == name }?.let { return it }
        PsiTreeUtil.findChildrenOfType(file, SabakaStructDecl::class.java)
            .firstOrNull { it.name == name }?.let { return it }
        PsiTreeUtil.findChildrenOfType(file, SabakaEnumDecl::class.java)
            .firstOrNull { it.name == name }?.let { return it }
        PsiTreeUtil.findChildrenOfType(file, SabakaInterfaceDecl::class.java)
            .firstOrNull { it.name == name }?.let { return it }

        // Local vars / params (walk up)
        var scope: PsiElement? = element.parent
        while (scope != null) {
            PsiTreeUtil.getChildrenOfType(scope, SabakaVarDeclStmt::class.java)
                ?.firstOrNull { it.name == name }?.let { return it }
            PsiTreeUtil.getChildrenOfType(scope, SabakaParam::class.java)
                ?.firstOrNull { it.name == name }?.let { return it }
            if (scope is SabakaFuncDecl || scope is SabakaFile) break
            scope = scope.parent
        }

        // Enum members
        PsiTreeUtil.findChildrenOfType(file, SabakaEnumMember::class.java)
            .firstOrNull { it.name == name }?.let { return it }

        return null
    }

    override fun getVariants(): Array<Any> = emptyArray()
}
