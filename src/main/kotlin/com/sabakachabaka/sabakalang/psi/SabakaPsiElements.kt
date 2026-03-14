package com.sabakachabaka.sabakalang.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.ASTNode
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiNamedElement
import com.sabakachabaka.sabakalang.SabakaFileType
import com.sabakachabaka.sabakalang.SabakaLanguage
import com.sabakachabaka.sabakalang.lexer.SabakaTokenTypes

// ── PSI File ──────────────────────────────────────────────────────────────────

class SabakaFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, SabakaLanguage) {
    override fun getFileType(): FileType = SabakaFileType.INSTANCE
    override fun toString() = "SabakaFile($name)"
}

// ── Base named element ────────────────────────────────────────────────────────

abstract class SabakaNamedElement(node: ASTNode) : ASTWrapperPsiElement(node), PsiNameIdentifierOwner {

    override fun getName(): String? = nameIdentifier?.text

    override fun setName(name: String): PsiElement {
        nameIdentifier?.replace(SabakaPsiFactory.createIdentifier(project, name))
        return this
    }

    override fun getNameIdentifier(): PsiElement? =
        findChildByType(SabakaTokenTypes.IDENTIFIER)

    override fun getTextOffset(): Int =
        nameIdentifier?.textOffset ?: super.getTextOffset()
}

// ── FUNC_DECL — top-level function, e.g.  void main() { }
//               or  int add(int a, int b) { return a + b; }
class SabakaFuncDecl(node: ASTNode) : SabakaNamedElement(node) {
    fun getParamList(): PsiElement? = findChildByType(SabakaElementTypes.PARAM_LIST)
    fun getBlock(): PsiElement?     = findChildByType(SabakaElementTypes.BLOCK)
}

// ── CLASS_DECL ────────────────────────────────────────────────────────────────
class SabakaClassDecl(node: ASTNode) : SabakaNamedElement(node) {
    fun getBody(): PsiElement? = findChildByType(SabakaElementTypes.CLASS_BODY)
}

// ── STRUCT_DECL ───────────────────────────────────────────────────────────────
class SabakaStructDecl(node: ASTNode) : SabakaNamedElement(node) {
    fun getBody(): PsiElement? = findChildByType(SabakaElementTypes.STRUCT_BODY)
}

// ── ENUM_DECL ─────────────────────────────────────────────────────────────────
class SabakaEnumDecl(node: ASTNode) : SabakaNamedElement(node) {
    fun getBody(): PsiElement? = findChildByType(SabakaElementTypes.ENUM_BODY)
}

// ── INTERFACE_DECL ────────────────────────────────────────────────────────────
class SabakaInterfaceDecl(node: ASTNode) : SabakaNamedElement(node) {
    fun getBody(): PsiElement? = findChildByType(SabakaElementTypes.INTERFACE_BODY)
}

// ── METHOD_DECL — method inside a class ──────────────────────────────────────
class SabakaMethodDecl(node: ASTNode) : SabakaNamedElement(node)

// ── FIELD_DECL — field inside class/struct ───────────────────────────────────
class SabakaFieldDecl(node: ASTNode) : SabakaNamedElement(node)

// ── PARAM ─────────────────────────────────────────────────────────────────────
class SabakaParam(node: ASTNode) : SabakaNamedElement(node)

// ── VAR_DECL_STMT — local variable declaration ───────────────────────────────
class SabakaVarDeclStmt(node: ASTNode) : SabakaNamedElement(node)

// ── ENUM_MEMBER ───────────────────────────────────────────────────────────────
class SabakaEnumMember(node: ASTNode) : SabakaNamedElement(node)

// ── VAR_EXPR — variable reference in expression ──────────────────────────────
class SabakaVarExpr(node: ASTNode) : ASTWrapperPsiElement(node), PsiNamedElement {
    override fun getName(): String? = text
    override fun setName(name: String): PsiElement = this
}

// ── Generic composite fallback ────────────────────────────────────────────────
class SabakaCompositeElement(node: ASTNode) : ASTWrapperPsiElement(node)
