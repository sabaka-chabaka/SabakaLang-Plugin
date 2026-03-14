package com.sabakachabaka.sabakalang.parser

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import com.sabakachabaka.sabakalang.lexer.SabakaLexer
import com.sabakachabaka.sabakalang.lexer.SabakaTokenTypes
import com.sabakachabaka.sabakalang.psi.*

class SabakaParserDefinition : ParserDefinition {

    override fun createLexer(project: Project): Lexer = SabakaLexer()
    override fun createParser(project: Project): PsiParser = SabakaParser()
    override fun getFileNodeType(): IFileElementType = SabakaElementTypes.FILE
    override fun getCommentTokens(): TokenSet = SabakaTokenTypes.COMMENTS
    override fun getStringLiteralElements(): TokenSet = TokenSet.create(SabakaTokenTypes.STRING_LITERAL)
    override fun getWhitespaceTokens(): TokenSet = SabakaTokenTypes.WHITESPACES

    override fun createElement(node: ASTNode): PsiElement = when (node.elementType) {
        SabakaElementTypes.FUNC_DECL      -> SabakaFuncDecl(node)
        SabakaElementTypes.METHOD_DECL    -> SabakaMethodDecl(node)
        SabakaElementTypes.CLASS_DECL     -> SabakaClassDecl(node)
        SabakaElementTypes.STRUCT_DECL    -> SabakaStructDecl(node)
        SabakaElementTypes.ENUM_DECL      -> SabakaEnumDecl(node)
        SabakaElementTypes.INTERFACE_DECL -> SabakaInterfaceDecl(node)
        SabakaElementTypes.FIELD_DECL     -> SabakaFieldDecl(node)
        SabakaElementTypes.PARAM          -> SabakaParam(node)
        SabakaElementTypes.VAR_DECL_STMT  -> SabakaVarDeclStmt(node)
        SabakaElementTypes.VAR_EXPR       -> SabakaVarExpr(node)
        SabakaElementTypes.ENUM_MEMBER    -> SabakaEnumMember(node)
        else                              -> SabakaCompositeElement(node)
    }

    override fun createFile(viewProvider: FileViewProvider): PsiFile = SabakaFile(viewProvider)
}
