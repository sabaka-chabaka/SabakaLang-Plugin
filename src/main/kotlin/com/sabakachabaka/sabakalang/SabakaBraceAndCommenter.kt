package com.sabakachabaka.sabakalang

import com.intellij.lang.BracePair
import com.intellij.lang.CodeDocumentationAwareCommenter
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.sabakachabaka.sabakalang.lexer.SabakaTokenTypes

class SabakaBraceMatcher : PairedBraceMatcher {
    override fun getPairs(): Array<BracePair> = arrayOf(
        BracePair(SabakaTokenTypes.LBRACE,   SabakaTokenTypes.RBRACE,   true),
        BracePair(SabakaTokenTypes.LPAREN,   SabakaTokenTypes.RPAREN,   false),
        BracePair(SabakaTokenTypes.LBRACKET, SabakaTokenTypes.RBRACKET, false),
    )
    override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?) = true
    override fun getCodeConstructStart(file: PsiFile, openingBraceOffset: Int) = openingBraceOffset
}

class SabakaCommenter : CodeDocumentationAwareCommenter {
    override fun getLineCommentPrefix()                = "//"
    override fun getBlockCommentPrefix()               = null
    override fun getBlockCommentSuffix()               = null
    override fun getCommentedBlockCommentPrefix()      = null
    override fun getCommentedBlockCommentSuffix()      = null
    override fun getLineCommentTokenType()             = SabakaTokenTypes.COMMENT
    override fun getBlockCommentTokenType()            = null
    override fun getDocumentationCommentTokenType()    = null
    override fun getDocumentationCommentPrefix()       = null
    override fun getDocumentationCommentLinePrefix()   = null
    override fun getDocumentationCommentSuffix()       = null
    override fun isDocumentationComment(element: PsiComment) = false
}
