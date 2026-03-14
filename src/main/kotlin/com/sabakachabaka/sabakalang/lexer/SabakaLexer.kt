package com.sabakachabaka.sabakalang.lexer

import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType
import com.sabakachabaka.sabakalang.lexer.SabakaTokenTypes.*

/**
 * Hand-written incremental lexer for SabakaLang.
 *
 * Mirrors the original C# Lexer.cs exactly:
 * - `//` line comments
 * - `"..."` string literals with escape sequences
 * - Integer and float literals (optional `f`/`F` suffix)
 * - All keywords from the original TokenType enum
 * - All operators and punctuation
 * - NO `func` keyword — functions are: `ReturnType name(...) { }`
 */
class SabakaLexer : LexerBase() {

    private var buffer: CharSequence = ""
    private var bufEnd: Int = 0
    private var tokenStart: Int = 0
    private var tokenEnd: Int = 0
    private var tokenType: IElementType? = null

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer  = buffer
        this.bufEnd  = endOffset
        this.tokenStart = startOffset
        this.tokenEnd   = startOffset
        advance()
    }

    override fun getState(): Int = 0
    override fun getTokenType(): IElementType? = tokenType
    override fun getTokenStart(): Int = tokenStart
    override fun getTokenEnd(): Int = tokenEnd
    override fun getBufferSequence(): CharSequence = buffer
    override fun getBufferEnd(): Int = bufEnd

    override fun advance() {
        tokenStart = tokenEnd
        if (tokenStart >= bufEnd) { tokenType = null; return }
        tokenType = nextToken()
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private fun ch(): Char  = if (tokenEnd < bufEnd) buffer[tokenEnd] else '\u0000'
    private fun peek(): Char = if (tokenEnd + 1 < bufEnd) buffer[tokenEnd + 1] else '\u0000'
    private fun eat()        { tokenEnd++ }

    // ── Main dispatch ─────────────────────────────────────────────────────────

    private fun nextToken(): IElementType {
        val c = ch()

        // Whitespace
        if (c.isWhitespace()) {
            while (tokenEnd < bufEnd && buffer[tokenEnd].isWhitespace()) eat()
            return WHITE_SPACE
        }

        // Line comment  //
        if (c == '/' && peek() == '/') {
            while (tokenEnd < bufEnd && buffer[tokenEnd] != '\n' && buffer[tokenEnd] != '\r') eat()
            return COMMENT
        }

        // Numeric literal
        if (c.isDigit()) return readNumber()

        // Identifier or keyword
        if (c.isLetter() || c == '_') return readIdentifier()

        // String literal
        if (c == '"') return readString()

        // Single-character and multi-character operators/punctuation
        eat()
        return when (c) {
            '+'  -> PLUS
            '-'  -> MINUS
            '*'  -> STAR
            '%'  -> PERCENT
            '/'  -> SLASH
            ';'  -> SEMICOLON
            ','  -> COMMA
            '.'  -> DOT
            '('  -> LPAREN
            ')'  -> RPAREN
            '{'  -> LBRACE
            '}'  -> RBRACE
            '['  -> LBRACKET
            ']'  -> RBRACKET
            '='  -> if (ch() == '=')  { eat(); EQEQ  } else EQ
            '!'  -> if (ch() == '=')  { eat(); NEQ   } else BANG
            '>'  -> if (ch() == '=')  { eat(); GTE   } else GT
            '<'  -> if (ch() == '=')  { eat(); LTE   } else LT
            '&'  -> if (ch() == '&')  { eat(); ANDAND } else BAD_CHARACTER
            '|'  -> if (ch() == '|')  { eat(); OROR  } else BAD_CHARACTER
            ':'  -> if (ch() == ':')  { eat(); COLONCOLON } else COLON
            else -> BAD_CHARACTER
        }
    }

    // ── Number ────────────────────────────────────────────────────────────────

    private fun readNumber(): IElementType {
        var isFloat = false
        while (tokenEnd < bufEnd && buffer[tokenEnd].isDigit()) eat()
        // Decimal part: 3.14
        if (tokenEnd < bufEnd && buffer[tokenEnd] == '.' &&
            tokenEnd + 1 < bufEnd && buffer[tokenEnd + 1].isDigit()
        ) {
            isFloat = true
            eat() // '.'
            while (tokenEnd < bufEnd && buffer[tokenEnd].isDigit()) eat()
        }
        // Optional float suffix: 1.0f or 1f
        if (tokenEnd < bufEnd && (buffer[tokenEnd] == 'f' || buffer[tokenEnd] == 'F')) {
            isFloat = true; eat()
        }
        return if (isFloat) FLOAT_LITERAL else INT_LITERAL
    }

    // ── Identifier / keyword ──────────────────────────────────────────────────

    private fun readIdentifier(): IElementType {
        while (tokenEnd < bufEnd && (buffer[tokenEnd].isLetterOrDigit() || buffer[tokenEnd] == '_')) eat()
        val text = buffer.subSequence(tokenStart, tokenEnd).toString()
        // true / false are BOOL_LITERAL; all others follow KEYWORD_MAP or IDENTIFIER
        return KEYWORD_MAP[text] ?: IDENTIFIER
    }

    // ── String literal ────────────────────────────────────────────────────────

    private fun readString(): IElementType {
        eat() // opening "
        while (tokenEnd < bufEnd && buffer[tokenEnd] != '"') {
            if (buffer[tokenEnd] == '\\' && tokenEnd + 1 < bufEnd) eat() // skip escape char
            if (tokenEnd < bufEnd) eat()
        }
        if (tokenEnd < bufEnd) eat() // closing "
        return STRING_LITERAL
    }
}
