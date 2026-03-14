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
import com.sabakachabaka.sabakalang.lexer.SabakaTokenTypes.*

// ── Attribute keys ────────────────────────────────────────────────────────────

object SabakaColors {
    // Lexer-based
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

    // Semantic (set by annotator)
    @JvmField val FUNC_DECL    = createTextAttributesKey("SABAKA_FUNC_DECL",    DC.FUNCTION_DECLARATION)
    @JvmField val FUNC_CALL    = createTextAttributesKey("SABAKA_FUNC_CALL",    DC.FUNCTION_CALL)
    @JvmField val BUILTIN_CALL = createTextAttributesKey("SABAKA_BUILTIN_CALL", DC.PREDEFINED_SYMBOL)
    @JvmField val CLASS_NAME   = createTextAttributesKey("SABAKA_CLASS_NAME",   DC.CLASS_NAME)
    @JvmField val STRUCT_NAME  = createTextAttributesKey("SABAKA_STRUCT_NAME",  DC.CLASS_NAME)
    @JvmField val ENUM_NAME    = createTextAttributesKey("SABAKA_ENUM_NAME",    DC.CLASS_NAME)
    @JvmField val PARAM        = createTextAttributesKey("SABAKA_PARAM",        DC.PARAMETER)
    @JvmField val LOCAL_VAR    = createTextAttributesKey("SABAKA_LOCAL_VAR",    DC.LOCAL_VARIABLE)
}

// ── Lexer-based highlighter ───────────────────────────────────────────────────

class SabakaSyntaxHighlighter : SyntaxHighlighterBase() {

    override fun getHighlightingLexer(): Lexer = SabakaLexer()

    override fun getTokenHighlights(tt: IElementType): Array<TextAttributesKey> = when (tt) {
        KW_INT, KW_FLOAT, KW_BOOL, KW_STRING, KW_VOID -> pack(SabakaColors.TYPE_KEYWORD)

        KW_RETURN, KW_IF, KW_ELSE, KW_WHILE,
        KW_FOR, KW_FOREACH, KW_IN,
        KW_SWITCH, KW_CASE, KW_DEFAULT,
        KW_STRUCT, KW_ENUM, KW_CLASS, KW_INTERFACE,
        KW_NEW, KW_OVERRIDE, KW_SUPER,
        KW_PUBLIC, KW_PRIVATE, KW_PROTECTED,
        KW_IMPORT                              -> pack(SabakaColors.KEYWORD)

        INT_LITERAL, FLOAT_LITERAL             -> pack(SabakaColors.NUMBER)
        STRING_LITERAL                         -> pack(SabakaColors.STRING)
        BOOL_LITERAL                           -> pack(SabakaColors.BOOL_LIT)
        COMMENT                                -> pack(SabakaColors.COMMENT)

        PLUS, MINUS, STAR, SLASH, PERCENT,
        EQ, EQEQ, NEQ, GT, LT, GTE, LTE,
        ANDAND, OROR, BANG                     -> pack(SabakaColors.OPERATOR)

        LPAREN, RPAREN                         -> pack(SabakaColors.PAREN)
        LBRACE, RBRACE                         -> pack(SabakaColors.BRACE)
        LBRACKET, RBRACKET                     -> pack(SabakaColors.BRACKET)
        SEMICOLON                              -> pack(SabakaColors.SEMICOLON)
        COMMA                                  -> pack(SabakaColors.COMMA)
        DOT, COLON, COLONCOLON                 -> pack(SabakaColors.DOT)
        IDENTIFIER                             -> pack(SabakaColors.IDENTIFIER)
        BAD_CHARACTER                          -> pack(SabakaColors.BAD_CHAR)
        else                                   -> EMPTY
    }

    companion object {
        private val EMPTY = emptyArray<TextAttributesKey>()
        private fun pack(k: TextAttributesKey) = arrayOf(k)
    }
}

class SabakaSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, vf: VirtualFile?) = SabakaSyntaxHighlighter()
}
