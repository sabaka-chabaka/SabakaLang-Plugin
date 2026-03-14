package com.sabakachabaka.sabakalang.highlighting

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as DC
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.tree.IElementType
import com.sabakachabaka.sabakalang.lexer.SabakaLexer
import com.sabakachabaka.sabakalang.lexer.SabakaTokenTypes

object SabakaColors {
    @JvmField val KEYWORD      = createTextAttributesKey("SABAKA_KEYWORD",      DC.KEYWORD)
    @JvmField val TYPE_KEYWORD = createTextAttributesKey("SABAKA_TYPE_KEYWORD", DC.KEYWORD)
    @JvmField val IDENTIFIER   = createTextAttributesKey("SABAKA_IDENTIFIER",   DC.IDENTIFIER)
    @JvmField val NUMBER       = createTextAttributesKey("SABAKA_NUMBER",        DC.NUMBER)
    @JvmField val STRING       = createTextAttributesKey("SABAKA_STRING",        DC.STRING)
    @JvmField val COMMENT      = createTextAttributesKey("SABAKA_COMMENT",       DC.LINE_COMMENT)
    @JvmField val OPERATOR     = createTextAttributesKey("SABAKA_OPERATOR",      DC.OPERATION_SIGN)
    @JvmField val PAREN        = createTextAttributesKey("SABAKA_PAREN",         DC.PARENTHESES)
    @JvmField val BRACE        = createTextAttributesKey("SABAKA_BRACE",         DC.BRACES)
    @JvmField val BRACKET      = createTextAttributesKey("SABAKA_BRACKET",       DC.BRACKETS)
    @JvmField val SEMICOLON    = createTextAttributesKey("SABAKA_SEMICOLON",     DC.SEMICOLON)
    @JvmField val COMMA        = createTextAttributesKey("SABAKA_COMMA",         DC.COMMA)
    @JvmField val DOT          = createTextAttributesKey("SABAKA_DOT",           DC.DOT)
    @JvmField val BOOL_LIT     = createTextAttributesKey("SABAKA_BOOL_LITERAL",  DC.KEYWORD)
    @JvmField val BAD_CHAR     = createTextAttributesKey("SABAKA_BAD_CHAR",      DC.INVALID_STRING_ESCAPE)
    @JvmField val FUNC_DECL    = createTextAttributesKey("SABAKA_FUNC_DECL",    DC.FUNCTION_DECLARATION)
    @JvmField val FUNC_CALL    = createTextAttributesKey("SABAKA_FUNC_CALL",    DC.FUNCTION_CALL)
    @JvmField val BUILTIN_CALL = createTextAttributesKey("SABAKA_BUILTIN_CALL", DC.PREDEFINED_SYMBOL)
    @JvmField val CLASS_NAME   = createTextAttributesKey("SABAKA_CLASS_NAME",   DC.CLASS_NAME)
    @JvmField val STRUCT_NAME  = createTextAttributesKey("SABAKA_STRUCT_NAME",  DC.CLASS_NAME)
    @JvmField val ENUM_NAME    = createTextAttributesKey("SABAKA_ENUM_NAME",    DC.CLASS_NAME)
    @JvmField val PARAM        = createTextAttributesKey("SABAKA_PARAM",        DC.PARAMETER)
    @JvmField val LOCAL_VAR    = createTextAttributesKey("SABAKA_LOCAL_VAR",    DC.LOCAL_VARIABLE)
}

class SabakaSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer(): Lexer = SabakaLexer()

    override fun getTokenHighlights(tt: IElementType): Array<TextAttributesKey> = when (tt) {
        SabakaTokenTypes.KW_INT, SabakaTokenTypes.KW_FLOAT,
        SabakaTokenTypes.KW_BOOL, SabakaTokenTypes.KW_STRING,
        SabakaTokenTypes.KW_VOID                             -> pack(SabakaColors.TYPE_KEYWORD)

        SabakaTokenTypes.KW_RETURN, SabakaTokenTypes.KW_IF,
        SabakaTokenTypes.KW_ELSE,   SabakaTokenTypes.KW_WHILE,
        SabakaTokenTypes.KW_FOR,    SabakaTokenTypes.KW_FOREACH,
        SabakaTokenTypes.KW_IN,     SabakaTokenTypes.KW_SWITCH,
        SabakaTokenTypes.KW_CASE,   SabakaTokenTypes.KW_DEFAULT,
        SabakaTokenTypes.KW_STRUCT, SabakaTokenTypes.KW_ENUM,
        SabakaTokenTypes.KW_CLASS,  SabakaTokenTypes.KW_INTERFACE,
        SabakaTokenTypes.KW_NEW,    SabakaTokenTypes.KW_OVERRIDE,
        SabakaTokenTypes.KW_SUPER,  SabakaTokenTypes.KW_PUBLIC,
        SabakaTokenTypes.KW_PRIVATE,SabakaTokenTypes.KW_PROTECTED,
        SabakaTokenTypes.KW_IMPORT                           -> pack(SabakaColors.KEYWORD)

        SabakaTokenTypes.INT_LITERAL, SabakaTokenTypes.FLOAT_LITERAL -> pack(SabakaColors.NUMBER)
        SabakaTokenTypes.STRING_LITERAL                       -> pack(SabakaColors.STRING)
        SabakaTokenTypes.BOOL_LITERAL                         -> pack(SabakaColors.BOOL_LIT)
        SabakaTokenTypes.COMMENT                              -> pack(SabakaColors.COMMENT)

        SabakaTokenTypes.PLUS,   SabakaTokenTypes.MINUS,
        SabakaTokenTypes.STAR,   SabakaTokenTypes.SLASH,
        SabakaTokenTypes.PERCENT,SabakaTokenTypes.EQ,
        SabakaTokenTypes.EQEQ,  SabakaTokenTypes.NEQ,
        SabakaTokenTypes.GT,     SabakaTokenTypes.LT,
        SabakaTokenTypes.GTE,    SabakaTokenTypes.LTE,
        SabakaTokenTypes.ANDAND, SabakaTokenTypes.OROR,
        SabakaTokenTypes.BANG                                 -> pack(SabakaColors.OPERATOR)

        SabakaTokenTypes.LPAREN, SabakaTokenTypes.RPAREN     -> pack(SabakaColors.PAREN)
        SabakaTokenTypes.LBRACE, SabakaTokenTypes.RBRACE     -> pack(SabakaColors.BRACE)
        SabakaTokenTypes.LBRACKET, SabakaTokenTypes.RBRACKET -> pack(SabakaColors.BRACKET)
        SabakaTokenTypes.SEMICOLON                            -> pack(SabakaColors.SEMICOLON)
        SabakaTokenTypes.COMMA                                -> pack(SabakaColors.COMMA)
        SabakaTokenTypes.DOT, SabakaTokenTypes.COLON,
        SabakaTokenTypes.COLONCOLON                           -> pack(SabakaColors.DOT)
        SabakaTokenTypes.IDENTIFIER                           -> pack(SabakaColors.IDENTIFIER)
        SabakaTokenTypes.BAD_CHARACTER                        -> pack(SabakaColors.BAD_CHAR)
        else                                                  -> EMPTY
    }

    companion object {
        private val EMPTY = emptyArray<TextAttributesKey>()
        private fun pack(k: TextAttributesKey) = arrayOf(k)
    }
}

class SabakaSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, vf: VirtualFile?) = SabakaSyntaxHighlighter()
}
