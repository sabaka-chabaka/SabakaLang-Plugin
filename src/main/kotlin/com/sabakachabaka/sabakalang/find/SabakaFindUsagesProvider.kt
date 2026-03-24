package com.sabakachabaka.sabakalang.find

import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.tree.TokenSet
import com.sabakachabaka.sabakalang.lexer.SabakaLexer
import com.sabakachabaka.sabakalang.lexer.SabakaTokenTypes
import com.sabakachabaka.sabakalang.psi.*

class SabakaFindUsagesProvider : FindUsagesProvider {

    override fun canFindUsagesFor(element: PsiElement): Boolean =
        element is SabakaNamedElement

    override fun getHelpId(element: PsiElement): String? = null

    override fun getType(element: PsiElement): String = when (element) {
        is SabakaFuncDecl      -> "function"
        is SabakaMethodDecl    -> "method"
        is SabakaClassDecl     -> "class"
        is SabakaStructDecl    -> "struct"
        is SabakaEnumDecl      -> "enum"
        is SabakaInterfaceDecl -> "interface"
        is SabakaFieldDecl     -> "field"
        is SabakaParam         -> "parameter"
        is SabakaVarDeclStmt   -> "variable"
        is SabakaEnumMember    -> "enum member"
        else                   -> "identifier"
    }

    override fun getDescriptiveName(element: PsiElement): String =
        (element as? PsiNamedElement)?.name ?: element.text

    override fun getNodeText(element: PsiElement, useFullName: Boolean): String = when (element) {
        is SabakaFuncDecl   -> "${element.name}(...)"
        is SabakaClassDecl  -> "class ${element.name}"
        is SabakaStructDecl -> "struct ${element.name}"
        is SabakaEnumDecl   -> "enum ${element.name}"
        is SabakaParam      -> "${element.name} (param)"
        is SabakaVarDeclStmt -> "${element.name} (var)"
        else -> (element as? PsiNamedElement)?.name ?: element.text
    }

    override fun getWordsScanner() = DefaultWordsScanner(
        SabakaLexer(),
        TokenSet.create(SabakaTokenTypes.IDENTIFIER),
        TokenSet.create(SabakaTokenTypes.COMMENT),
        TokenSet.create(SabakaTokenTypes.STRING_LITERAL)
    )
}