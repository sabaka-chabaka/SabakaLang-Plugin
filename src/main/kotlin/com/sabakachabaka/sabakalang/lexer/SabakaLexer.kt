package com.sabakachabaka.sabakalang.lexer

import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType

/**
 * Hand-written incremental lexer for SabakaLang.
 * NO `func` keyword — functions are: ReturnType name(...) { }
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

    private fun ch(): Char   = if (tokenEnd < bufEnd) buffer[tokenEnd] else '\u0000'
    private fun peek(): Char = if (tokenEnd + 1 < bufEnd) buffer[tokenEnd + 1] else '\u0000'
    private fun eat()        { tokenEnd++ }

    private fun nextToken(): IElementType {
        val c = ch()

        if (c.isWhitespace()) {
            while (tokenEnd < bufEnd && buffer[tokenEnd].isWhitespace()) eat()
            return SabakaTokenTypes.WHITE_SPACE
        }

        if (c == '/' && peek() == '/') {
            while (tokenEnd < bufEnd && buffer[tokenEnd] != '\n' && buffer[tokenEnd] != '\r') eat()
            return SabakaTokenTypes.COMMENT
        }

        if (c.isDigit()) return readNumber()
        if (c.isLetter() || c == '_') return readIdentifier()
        if (c == '"') return readString()

        eat()
        return when (c) {
            '+'  -> SabakaTokenTypes.PLUS
            '-'  -> SabakaTokenTypes.MINUS
            '*'  -> SabakaTokenTypes.STAR
            '%'  -> SabakaTokenTypes.PERCENT
            '/'  -> SabakaTokenTypes.SLASH
            ';'  -> SabakaTokenTypes.SEMICOLON
            ','  -> SabakaTokenTypes.COMMA
            '.'  -> SabakaTokenTypes.DOT
            '('  -> SabakaTokenTypes.LPAREN
            ')'  -> SabakaTokenTypes.RPAREN
            '{'  -> SabakaTokenTypes.LBRACE
            '}'  -> SabakaTokenTypes.RBRACE
            '['  -> SabakaTokenTypes.LBRACKET
            ']'  -> SabakaTokenTypes.RBRACKET
            '='  -> if (ch() == '=')  { eat(); SabakaTokenTypes.EQEQ     } else SabakaTokenTypes.EQ
            '!'  -> if (ch() == '=')  { eat(); SabakaTokenTypes.NEQ      } else SabakaTokenTypes.BANG
            '>'  -> if (ch() == '=')  { eat(); SabakaTokenTypes.GTE      } else SabakaTokenTypes.GT
            '<'  -> if (ch() == '=')  { eat(); SabakaTokenTypes.LTE      } else SabakaTokenTypes.LT
            '&'  -> if (ch() == '&')  { eat(); SabakaTokenTypes.ANDAND   } else SabakaTokenTypes.BAD_CHARACTER
            '|'  -> if (ch() == '|')  { eat(); SabakaTokenTypes.OROR     } else SabakaTokenTypes.BAD_CHARACTER
            ':'  -> if (ch() == ':')  { eat(); SabakaTokenTypes.COLONCOLON } else SabakaTokenTypes.COLON
            else -> SabakaTokenTypes.BAD_CHARACTER
        }
    }

    private fun readNumber(): IElementType {
        var isFloat = false
        while (tokenEnd < bufEnd && buffer[tokenEnd].isDigit()) eat()
        if (tokenEnd < bufEnd && buffer[tokenEnd] == '.' &&
            tokenEnd + 1 < bufEnd && buffer[tokenEnd + 1].isDigit()) {
            isFloat = true; eat()
            while (tokenEnd < bufEnd && buffer[tokenEnd].isDigit()) eat()
        }
        if (tokenEnd < bufEnd && (buffer[tokenEnd] == 'f' || buffer[tokenEnd] == 'F')) {
            isFloat = true; eat()
        }
        return if (isFloat) SabakaTokenTypes.FLOAT_LITERAL else SabakaTokenTypes.INT_LITERAL
    }

    private fun readIdentifier(): IElementType {
        while (tokenEnd < bufEnd && (buffer[tokenEnd].isLetterOrDigit() || buffer[tokenEnd] == '_')) eat()
        val text = buffer.subSequence(tokenStart, tokenEnd).toString()
        return SabakaTokenTypes.KEYWORD_MAP[text] ?: SabakaTokenTypes.IDENTIFIER
    }

    private fun readString(): IElementType {
        eat() // opening "
        while (tokenEnd < bufEnd && buffer[tokenEnd] != '"') {
            if (buffer[tokenEnd] == '\\' && tokenEnd + 1 < bufEnd) eat()
            if (tokenEnd < bufEnd) eat()
        }
        if (tokenEnd < bufEnd) eat() // closing "
        return SabakaTokenTypes.STRING_LITERAL
    }
}
