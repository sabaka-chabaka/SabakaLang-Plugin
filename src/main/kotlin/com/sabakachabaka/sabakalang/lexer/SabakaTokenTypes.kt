package com.sabakachabaka.sabakalang.lexer

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.sabakachabaka.sabakalang.SabakaLanguage

class SabakaTokenType(debugName: String) : IElementType(debugName, SabakaLanguage) {
    override fun toString() = "SabakaTokenType.$debugName"
}

/**
 * All token types for SabakaLang.
 *
 * IMPORTANT: SabakaLang has NO `func` keyword.
 * Function syntax is: ReturnType name(params) { body }
 * Example: void main() { ... }
 *          int add(int a, int b) { return a + b; }
 *          void Dog() {}   ← constructor
 */
object SabakaTokenTypes {

    // ── Literals ──────────────────────────────────────────────────────────────
    @JvmField val INT_LITERAL    = SabakaTokenType("INT_LITERAL")
    @JvmField val FLOAT_LITERAL  = SabakaTokenType("FLOAT_LITERAL")
    @JvmField val STRING_LITERAL = SabakaTokenType("STRING_LITERAL")
    @JvmField val BOOL_LITERAL   = SabakaTokenType("BOOL_LITERAL")   // true / false

    // ── Identifier ────────────────────────────────────────────────────────────
    @JvmField val IDENTIFIER     = SabakaTokenType("IDENTIFIER")

    // ── Type keywords (built-in primitive types) ──────────────────────────────
    @JvmField val KW_INT         = SabakaTokenType("KW_INT")
    @JvmField val KW_FLOAT       = SabakaTokenType("KW_FLOAT")
    @JvmField val KW_BOOL        = SabakaTokenType("KW_BOOL")
    @JvmField val KW_STRING      = SabakaTokenType("KW_STRING")
    @JvmField val KW_VOID        = SabakaTokenType("KW_VOID")

    // ── Control-flow keywords ─────────────────────────────────────────────────
    @JvmField val KW_RETURN      = SabakaTokenType("KW_RETURN")
    @JvmField val KW_IF          = SabakaTokenType("KW_IF")
    @JvmField val KW_ELSE        = SabakaTokenType("KW_ELSE")
    @JvmField val KW_WHILE       = SabakaTokenType("KW_WHILE")
    @JvmField val KW_FOR         = SabakaTokenType("KW_FOR")
    @JvmField val KW_FOREACH     = SabakaTokenType("KW_FOREACH")
    @JvmField val KW_IN          = SabakaTokenType("KW_IN")
    @JvmField val KW_SWITCH      = SabakaTokenType("KW_SWITCH")
    @JvmField val KW_CASE        = SabakaTokenType("KW_CASE")
    @JvmField val KW_DEFAULT     = SabakaTokenType("KW_DEFAULT")

    // ── Declaration keywords ──────────────────────────────────────────────────
    @JvmField val KW_STRUCT      = SabakaTokenType("KW_STRUCT")
    @JvmField val KW_ENUM        = SabakaTokenType("KW_ENUM")
    @JvmField val KW_CLASS       = SabakaTokenType("KW_CLASS")
    @JvmField val KW_INTERFACE   = SabakaTokenType("KW_INTERFACE")
    @JvmField val KW_NEW         = SabakaTokenType("KW_NEW")
    @JvmField val KW_OVERRIDE    = SabakaTokenType("KW_OVERRIDE")
    @JvmField val KW_SUPER       = SabakaTokenType("KW_SUPER")
    @JvmField val KW_IMPORT      = SabakaTokenType("KW_IMPORT")

    // ── Access modifiers ──────────────────────────────────────────────────────
    @JvmField val KW_PUBLIC      = SabakaTokenType("KW_PUBLIC")
    @JvmField val KW_PRIVATE     = SabakaTokenType("KW_PRIVATE")
    @JvmField val KW_PROTECTED   = SabakaTokenType("KW_PROTECTED")

    // ── Operators ─────────────────────────────────────────────────────────────
    @JvmField val PLUS           = SabakaTokenType("PLUS")
    @JvmField val MINUS          = SabakaTokenType("MINUS")
    @JvmField val STAR           = SabakaTokenType("STAR")
    @JvmField val SLASH          = SabakaTokenType("SLASH")
    @JvmField val PERCENT        = SabakaTokenType("PERCENT")
    @JvmField val EQ             = SabakaTokenType("EQ")
    @JvmField val EQEQ           = SabakaTokenType("EQEQ")
    @JvmField val NEQ            = SabakaTokenType("NEQ")
    @JvmField val GT             = SabakaTokenType("GT")
    @JvmField val LT             = SabakaTokenType("LT")
    @JvmField val GTE            = SabakaTokenType("GTE")
    @JvmField val LTE            = SabakaTokenType("LTE")
    @JvmField val ANDAND         = SabakaTokenType("ANDAND")
    @JvmField val OROR           = SabakaTokenType("OROR")
    @JvmField val BANG           = SabakaTokenType("BANG")

    // ── Punctuation ───────────────────────────────────────────────────────────
    @JvmField val DOT            = SabakaTokenType("DOT")
    @JvmField val COLON          = SabakaTokenType("COLON")
    @JvmField val COLONCOLON     = SabakaTokenType("COLONCOLON")
    @JvmField val LPAREN         = SabakaTokenType("LPAREN")
    @JvmField val RPAREN         = SabakaTokenType("RPAREN")
    @JvmField val LBRACE         = SabakaTokenType("LBRACE")
    @JvmField val RBRACE         = SabakaTokenType("RBRACE")
    @JvmField val LBRACKET       = SabakaTokenType("LBRACKET")
    @JvmField val RBRACKET       = SabakaTokenType("RBRACKET")
    @JvmField val SEMICOLON      = SabakaTokenType("SEMICOLON")
    @JvmField val COMMA          = SabakaTokenType("COMMA")

    // ── Trivia ────────────────────────────────────────────────────────────────
    @JvmField val COMMENT        = SabakaTokenType("COMMENT")
    @JvmField val WHITE_SPACE    = SabakaTokenType("WHITE_SPACE")
    @JvmField val BAD_CHARACTER  = SabakaTokenType("BAD_CHARACTER")

    // ── Token sets ────────────────────────────────────────────────────────────

    /** All keywords that are not type keywords */
    @JvmField val KEYWORDS = TokenSet.create(
        KW_RETURN, KW_IF, KW_ELSE, KW_WHILE, KW_FOR, KW_FOREACH, KW_IN,
        KW_SWITCH, KW_CASE, KW_DEFAULT,
        KW_STRUCT, KW_ENUM, KW_CLASS, KW_INTERFACE,
        KW_NEW, KW_OVERRIDE, KW_SUPER, KW_IMPORT,
        KW_PUBLIC, KW_PRIVATE, KW_PROTECTED
    )

    /** Primitive type keywords used as return types and variable types */
    @JvmField val TYPE_KEYWORDS = TokenSet.create(
        KW_INT, KW_FLOAT, KW_BOOL, KW_STRING, KW_VOID
    )

    @JvmField val ACCESS_MODIFIERS = TokenSet.create(
        KW_PUBLIC, KW_PRIVATE, KW_PROTECTED
    )

    @JvmField val LITERALS = TokenSet.create(
        INT_LITERAL, FLOAT_LITERAL, STRING_LITERAL, BOOL_LITERAL
    )

    @JvmField val COMMENTS    = TokenSet.create(COMMENT)
    @JvmField val WHITESPACES = TokenSet.create(WHITE_SPACE)

    @JvmField val OPERATORS = TokenSet.create(
        PLUS, MINUS, STAR, SLASH, PERCENT,
        EQEQ, NEQ, GT, LT, GTE, LTE,
        ANDAND, OROR, BANG
    )
    // EQ (=) handled separately in formatter so assignments get spaces
    @JvmField val EQ_SET = TokenSet.create(EQ)

    @JvmField val BRACES   = TokenSet.create(LBRACE, RBRACE)
    @JvmField val BRACKETS = TokenSet.create(LBRACKET, RBRACKET)
    @JvmField val PARENS   = TokenSet.create(LPAREN, RPAREN)

    /**
     * All type-start tokens: both built-in types and user-defined type names (IDENTIFIER).
     * A function/variable declaration always starts with one of these.
     */
    @JvmField val ALL_TYPE_STARTS = TokenSet.create(
        KW_INT, KW_FLOAT, KW_BOOL, KW_STRING, KW_VOID, IDENTIFIER
    )

    // ── Keyword map (text → token type) ──────────────────────────────────────
    val KEYWORD_MAP: Map<String, SabakaTokenType> = mapOf(
        "int"       to KW_INT,
        "float"     to KW_FLOAT,
        "bool"      to KW_BOOL,
        "string"    to KW_STRING,
        "void"      to KW_VOID,
        "return"    to KW_RETURN,
        "if"        to KW_IF,
        "else"      to KW_ELSE,
        "while"     to KW_WHILE,
        "for"       to KW_FOR,
        "foreach"   to KW_FOREACH,
        "in"        to KW_IN,
        "switch"    to KW_SWITCH,
        "case"      to KW_CASE,
        "default"   to KW_DEFAULT,
        "struct"    to KW_STRUCT,
        "enum"      to KW_ENUM,
        "class"     to KW_CLASS,
        "interface" to KW_INTERFACE,
        "new"       to KW_NEW,
        "override"  to KW_OVERRIDE,
        "super"     to KW_SUPER,
        "import"    to KW_IMPORT,
        "public"    to KW_PUBLIC,
        "private"   to KW_PRIVATE,
        "protected" to KW_PROTECTED,
        // Literals handled as separate token types
        "true"      to BOOL_LITERAL,
        "false"     to BOOL_LITERAL,
    )
}
