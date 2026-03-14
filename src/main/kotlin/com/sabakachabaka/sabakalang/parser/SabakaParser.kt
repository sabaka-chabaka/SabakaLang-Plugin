package com.sabakachabaka.sabakalang.parser

import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.psi.tree.IElementType
import com.sabakachabaka.sabakalang.lexer.SabakaTokenTypes
import com.sabakachabaka.sabakalang.lexer.SabakaTokenTypes.*
import com.sabakachabaka.sabakalang.psi.SabakaElementTypes
import com.sabakachabaka.sabakalang.psi.SabakaElementTypes.*

/**
 * Recursive-descent parser for SabakaLang.
 *
 * KEY GRAMMAR FACTS (from Parser.cs + list.sabaka example):
 *
 *  Function declaration (NO `func` keyword!):
 *    ReturnType name(ParamType paramName, ...) { body }
 *    Examples:
 *      void main() { }
 *      int add(int a, int b) { return a + b; }
 *      T getValue(int index) { return values[index]; }
 *      void List() {}            ← constructor (name == class name)
 *      void push_back(T value){} ← underscore in names is valid
 *
 *  Variable declaration:
 *    Type name = expr;
 *    Type name;
 *    T[] values = [];
 *
 *  Disambiguation: a statement starting with a type followed by an identifier
 *  and then `(` is a function declaration; followed by `=` or `;` is a var decl.
 */
class SabakaParser : PsiParser {
    override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
        val m = builder.mark()
        ParseContext(builder).parseFile()
        m.done(root)
        return builder.treeBuilt
    }
}

// ── Parse context ─────────────────────────────────────────────────────────────

private class ParseContext(private val b: PsiBuilder) {

    private fun tt()                    = b.tokenType
    private fun at(t: IElementType)     = tt() == t
    private fun eof()                   = b.eof()
    private fun advance()               { b.advanceLexer() }
    private fun mark()                  = b.mark()
    private fun text()                  = b.tokenText ?: ""

    private fun expect(t: IElementType) {
        if (at(t)) advance()
        else b.error("Expected $t but got ${tt()} ('${text()}')")
    }

    private fun consume(t: IElementType): Boolean {
        if (!at(t)) return false
        advance(); return true
    }

    // A token starts a type: int / float / bool / string / void / Identifier
    private fun isTypeStart() = tt() in ALL_TYPE_STARTS

    private fun isAccessModifier() = tt() in ACCESS_MODIFIERS

    // ── File ──────────────────────────────────────────────────────────────────

    fun parseFile() {
        while (!eof()) {
            when {
                at(KW_IMPORT)    -> parseImport()
                at(KW_CLASS)     -> parseClass()
                at(KW_STRUCT)    -> parseStruct()
                at(KW_ENUM)      -> parseEnum()
                at(KW_INTERFACE) -> parseInterface()
                // access modifier before class/struct/interface
                isAccessModifier() && peekAfterModifierIsDecl() -> parseAccessModifiedDecl()
                // Function or top-level statement: both start with a type token
                isTypeStart()    -> parseTopLevelTypeStart()
                else             -> { b.error("Unexpected '${text()}'"); advance() }
            }
        }
    }

    /** Peek past optional access-modifier (and override) to see if next is class/struct/enum/interface */
    private fun peekAfterModifierIsDecl(): Boolean {
        val saved = mark()
        advance() // skip access modifier
        val result = at(KW_CLASS) || at(KW_STRUCT) || at(KW_ENUM) || at(KW_INTERFACE)
        saved.rollbackTo()
        return result
    }

    private fun parseAccessModifiedDecl() {
        advance() // consume access modifier
        when {
            at(KW_CLASS)     -> parseClass()
            at(KW_STRUCT)    -> parseStruct()
            at(KW_ENUM)      -> parseEnum()
            at(KW_INTERFACE) -> parseInterface()
            else             -> b.error("Expected class/struct/enum/interface after access modifier")
        }
    }

    /**
     * Handles anything that starts with a type token at file (top) level.
     * Uses lookahead to decide: function declaration vs. top-level statement.
     *
     * Function: [access] [override] TypeName name(...)  { }
     * Variable: TypeName name = expr ;   or   TypeName name ;
     */
    private fun parseTopLevelTypeStart() {
        if (isFunctionDeclaration()) parseFuncDecl()
        else parseStatement()   // top-level var decl or expression statement
    }

    // ── Import ────────────────────────────────────────────────────────────────

    private fun parseImport() {
        val m = mark()
        expect(KW_IMPORT)
        when {
            at(LBRACE) -> {
                advance() // {
                while (!eof() && !at(RBRACE)) {
                    if (at(IDENTIFIER)) advance()
                    consume(COMMA)
                }
                expect(RBRACE)
                // optional  from "path"
                if (at(IDENTIFIER) && text() == "from") advance()
                if (at(STRING_LITERAL)) advance()
            }
            at(STRING_LITERAL) -> advance()
            at(IDENTIFIER) -> {
                advance()
                if (at(EQ)) { advance(); if (at(STRING_LITERAL)) advance() }
            }
        }
        consume(SEMICOLON)
        m.done(IMPORT_STMT)
    }

    // ── Function declaration ──────────────────────────────────────────────────
    //
    // Grammar: [access] [override] ReturnType name [<TypeParams>] ( params ) { body }
    //
    // Called both at top level and inside class body.

    fun parseFuncDecl(inClass: Boolean = false) {
        val m = mark()
        // optional access modifier
        if (isAccessModifier()) advance()
        // optional override
        consume(KW_OVERRIDE)
        // return type (may be an array type or generic, e.g. T[])
        parseTypeRef()
        // function name
        expect(IDENTIFIER)
        // optional generic type params: <T>  <T, U>
        parseOptionalTypeParams()
        // parameters
        parseParamList()
        // body
        parseBlock()
        m.done(if (inClass) METHOD_DECL else FUNC_DECL)
    }

    private fun parseParamList() {
        val m = mark()
        expect(LPAREN)
        while (!eof() && !at(RPAREN)) {
            val pm = mark()
            parseTypeRef()         // param type  (e.g. int, T, string[])
            expect(IDENTIFIER)     // param name
            pm.done(PARAM)
            if (!consume(COMMA)) break
        }
        expect(RPAREN)
        m.done(PARAM_LIST)
    }

    // ── Class ─────────────────────────────────────────────────────────────────

    private fun parseClass() {
        val m = mark()
        if (isAccessModifier()) advance()
        expect(KW_CLASS)
        expect(IDENTIFIER)
        parseOptionalTypeParams()
        // optional : BaseClass, Interface1, Interface2
        if (at(COLON)) {
            advance()
            expect(IDENTIFIER)
            while (consume(COMMA)) expect(IDENTIFIER)
        }
        val body = mark()
        expect(LBRACE)
        while (!eof() && !at(RBRACE)) {
            parseClassMember()
        }
        expect(RBRACE)
        body.done(CLASS_BODY)
        m.done(CLASS_DECL)
    }

    private fun parseClassMember() {
        when {
            isFunctionDeclaration() -> parseFuncDecl(inClass = true)
            isTypeStart()           -> parseFieldDecl()
            else -> { b.error("Expected class member, got '${text()}'"); advance() }
        }
    }

    private fun parseFieldDecl() {
        val m = mark()
        parseTypeRef()
        expect(IDENTIFIER)
        if (at(EQ)) {
            advance()
            parseExpr()
        }
        consume(SEMICOLON)
        m.done(FIELD_DECL)
    }

    // ── Struct ────────────────────────────────────────────────────────────────

    private fun parseStruct() {
        val m = mark()
        if (isAccessModifier()) advance()
        expect(KW_STRUCT)
        expect(IDENTIFIER)
        val body = mark()
        expect(LBRACE)
        while (!eof() && !at(RBRACE)) {
            if (isTypeStart()) {
                val fm = mark()
                parseTypeRef()
                expect(IDENTIFIER)
                consume(SEMICOLON)
                fm.done(FIELD_DECL)
            } else {
                b.error("Expected field"); advance()
            }
        }
        expect(RBRACE)
        body.done(STRUCT_BODY)
        m.done(STRUCT_DECL)
    }

    // ── Enum ──────────────────────────────────────────────────────────────────

    private fun parseEnum() {
        val m = mark()
        if (isAccessModifier()) advance()
        expect(KW_ENUM)
        expect(IDENTIFIER)
        val body = mark()
        expect(LBRACE)
        while (!eof() && !at(RBRACE)) {
            val em = mark()
            expect(IDENTIFIER)
            consume(COMMA)
            em.done(ENUM_MEMBER)
        }
        expect(RBRACE)
        body.done(ENUM_BODY)
        m.done(ENUM_DECL)
    }

    // ── Interface ─────────────────────────────────────────────────────────────
    // Interface methods have no body: ReturnType name(params);

    private fun parseInterface() {
        val m = mark()
        if (isAccessModifier()) advance()
        expect(KW_INTERFACE)
        expect(IDENTIFIER)
        parseOptionalTypeParams()
        // optional : Parent1, Parent2
        if (at(COLON)) {
            advance()
            expect(IDENTIFIER)
            while (consume(COMMA)) expect(IDENTIFIER)
        }
        val body = mark()
        expect(LBRACE)
        while (!eof() && !at(RBRACE)) {
            val mm = mark()
            if (isAccessModifier()) advance()
            parseTypeRef()         // return type
            expect(IDENTIFIER)     // method name
            parseParamList()
            consume(SEMICOLON)
            mm.done(INTERFACE_METHOD)
        }
        expect(RBRACE)
        body.done(INTERFACE_BODY)
        m.done(INTERFACE_DECL)
    }

    // ── Block ─────────────────────────────────────────────────────────────────

    private fun parseBlock() {
        val m = mark()
        expect(LBRACE)
        while (!eof() && !at(RBRACE)) parseStatement()
        expect(RBRACE)
        m.done(BLOCK)
    }

    // ── Statements ────────────────────────────────────────────────────────────

    private fun parseStatement() {
        when {
            isTypeStart() && isVarDecl() -> parseVarDeclStmt()
            at(KW_RETURN)   -> parseReturnStmt()
            at(KW_IF)       -> parseIfStmt()
            at(KW_WHILE)    -> parseWhileStmt()
            at(KW_FOR)      -> parseForStmt()
            at(KW_FOREACH)  -> parseForeachStmt()
            at(KW_SWITCH)   -> parseSwitchStmt()
            at(LBRACE)      -> parseBlock()
            else            -> parseExprStmt()
        }
    }

    private fun parseVarDeclStmt() {
        val m = mark()
        parseTypeRef()
        expect(IDENTIFIER)
        if (at(EQ)) { advance(); parseExpr() }
        consume(SEMICOLON)
        m.done(VAR_DECL_STMT)
    }

    private fun parseReturnStmt() {
        val m = mark()
        expect(KW_RETURN)
        if (!at(SEMICOLON) && !at(RBRACE) && !eof()) parseExpr()
        consume(SEMICOLON)
        m.done(RETURN_STMT)
    }

    private fun parseIfStmt() {
        val m = mark()
        expect(KW_IF)
        expect(LPAREN); parseExpr(); expect(RPAREN)
        parseStatement()
        if (at(KW_ELSE)) { advance(); parseStatement() }
        m.done(IF_STMT)
    }

    private fun parseWhileStmt() {
        val m = mark()
        expect(KW_WHILE)
        expect(LPAREN); parseExpr(); expect(RPAREN)
        parseStatement()
        m.done(WHILE_STMT)
    }

    private fun parseForStmt() {
        val m = mark()
        expect(KW_FOR)
        expect(LPAREN)
        // init
        if (!at(SEMICOLON)) {
            if (isTypeStart() && isVarDecl()) parseVarDeclStmt()
            else parseExprStmt()
        } else advance()
        // condition
        if (!at(SEMICOLON)) parseExpr()
        consume(SEMICOLON)
        // increment
        if (!at(RPAREN)) parseExpr()
        expect(RPAREN)
        parseStatement()
        m.done(FOR_STMT)
    }

    private fun parseForeachStmt() {
        val m = mark()
        expect(KW_FOREACH)
        expect(LPAREN)
        parseTypeRef()
        expect(IDENTIFIER)
        expect(KW_IN)
        parseExpr()
        expect(RPAREN)
        parseStatement()
        m.done(FOREACH_STMT)
    }

    private fun parseSwitchStmt() {
        val m = mark()
        expect(KW_SWITCH)
        expect(LPAREN); parseExpr(); expect(RPAREN)
        expect(LBRACE)
        while (!eof() && !at(RBRACE)) {
            val cm = mark()
            when {
                at(KW_CASE) -> {
                    advance(); parseExpr(); expect(COLON)
                    while (!eof() && !at(KW_CASE) && !at(KW_DEFAULT) && !at(RBRACE))
                        parseStatement()
                    cm.done(CASE_CLAUSE)
                }
                at(KW_DEFAULT) -> {
                    advance(); expect(COLON)
                    while (!eof() && !at(KW_CASE) && !at(RBRACE))
                        parseStatement()
                    cm.done(CASE_CLAUSE)
                }
                else -> { b.error("Expected 'case' or 'default'"); advance(); cm.drop() }
            }
        }
        expect(RBRACE)
        m.done(SWITCH_STMT)
    }

    private fun parseExprStmt() {
        val m = mark()
        parseExpr()
        consume(SEMICOLON)
        m.done(EXPR_STMT)
    }

    // ── Expressions (Pratt-style) ─────────────────────────────────────────────

    private fun parseExpr() = parseAssign()

    private fun parseAssign() {
        val m = mark()
        parseOr()
        if (at(EQ)) {
            advance(); parseAssign()
            m.done(ASSIGN_STMT)
        } else m.drop()
    }

    private fun parseOr()        = parseBinary({ parseAnd() },       OROR)
    private fun parseAnd()       = parseBinary({ parseEquality() },  ANDAND)
    private fun parseEquality()  = parseBinary({ parseRelational() }, EQEQ, NEQ)
    private fun parseRelational()= parseBinary({ parseAddSub() },    LT, GT, LTE, GTE)
    private fun parseAddSub()    = parseBinary({ parseMulDiv() },     PLUS, MINUS)
    private fun parseMulDiv()    = parseBinary({ parseUnary() },      STAR, SLASH, PERCENT)

    private fun parseBinary(sub: () -> Unit, vararg ops: IElementType) {
        sub()
        while (!eof() && tt() in ops) { advance(); sub() }
    }

    private fun parseUnary() {
        if (at(MINUS) || at(BANG)) {
            val m = mark(); advance(); parseUnary(); m.done(UNARY_EXPR)
        } else {
            parsePostfix()
        }
    }

    private fun parsePostfix() {
        parsePrimary()
        while (true) {
            when {
                at(DOT) -> {
                    advance(); expect(IDENTIFIER)
                    if (at(LPAREN)) parseArgList()
                }
                at(COLONCOLON) -> {
                    advance()
                    if (at(IDENTIFIER)) advance()
                    if (at(LPAREN)) parseArgList()
                }
                at(LBRACKET) -> {
                    advance(); parseExpr(); expect(RBRACKET)
                }
                else -> break
            }
        }
    }

    private fun parsePrimary() {
        val m = mark()
        when {
            at(INT_LITERAL) || at(FLOAT_LITERAL) || at(STRING_LITERAL) || at(BOOL_LITERAL) -> {
                advance(); m.done(LITERAL_EXPR)
            }
            at(KW_NEW) -> {
                advance()
                expect(IDENTIFIER)
                parseOptionalTypeParams()
                when {
                    at(LBRACKET) -> { advance(); parseExpr(); expect(RBRACKET) }
                    at(LPAREN)   -> parseArgList()
                }
                m.done(NEW_EXPR)
            }
            at(KW_SUPER) -> { advance(); m.done(SUPER_EXPR) }
            at(LBRACKET) -> {
                advance()
                while (!eof() && !at(RBRACKET)) {
                    parseExpr(); if (!consume(COMMA)) break
                }
                expect(RBRACKET)
                m.done(ARRAY_LITERAL)
            }
            at(IDENTIFIER) -> {
                advance()
                if (at(LPAREN)) { parseArgList(); m.done(CALL_EXPR) }
                else m.done(VAR_EXPR)
            }
            at(LPAREN) -> { advance(); parseExpr(); expect(RPAREN); m.drop() }
            else -> {
                if (!eof()) { b.error("Expected expression, got '${text()}'"); advance() }
                m.drop()
            }
        }
    }

    private fun parseArgList() {
        val m = mark()
        expect(LPAREN)
        while (!eof() && !at(RPAREN)) {
            parseExpr(); if (!consume(COMMA)) break
        }
        expect(RPAREN)
        m.done(ARG_LIST)
    }

    // ── Type references ───────────────────────────────────────────────────────
    //
    // Types: int, float, bool, string, void, SomeClass, T, Box<int>, int[], T[]

    private fun parseTypeRef() {
        val m = mark()
        when {
            tt() in TYPE_KEYWORDS -> advance()
            at(IDENTIFIER)        -> {
                advance()
                // Generic: Box<int>  or  T  alone
                if (at(LT)) parseOptionalTypeParams()
            }
            else -> b.error("Expected type, got '${text()}'")
        }
        // Array suffix: []
        while (at(LBRACKET) && peekIsRBracket()) {
            advance(); expect(RBRACKET)
        }
        m.done(TYPE_REF)
    }

    private fun peekIsRBracket(): Boolean {
        val s = mark()
        advance() // [
        val r = at(RBRACKET) || at(RBRACKET) // next is ] → true
        s.rollbackTo()
        // re-check after rollback
        val s2 = mark()
        advance()
        val result = at(RBRACKET)
        s2.rollbackTo()
        return result
    }

    /** Parses optional <T>, <T, U> etc. */
    private fun parseOptionalTypeParams() {
        if (!at(LT)) return
        val saved = mark()
        advance() // <
        var depth = 1
        while (!eof() && depth > 0) {
            when {
                at(LT)        -> { depth++; advance() }
                at(GT)        -> { depth--; advance() }
                at(SEMICOLON) -> break
                else          -> advance()
            }
        }
        if (depth == 0) saved.drop()
        else saved.rollbackTo()
    }

    // ── Lookahead helpers ─────────────────────────────────────────────────────

    /**
     * Determines if the current position starts a function declaration.
     * Logic mirrors Parser.cs IsFunctionDeclaration():
     *   [access] [override] TypeName [<>] [][] IDENTIFIER LPAREN
     */
    fun isFunctionDeclaration(): Boolean {
        val saved = mark()
        try {
            // optional access modifier
            if (isAccessModifier()) advance()
            // optional override
            if (at(KW_OVERRIDE)) advance()
            // return type must be a type keyword or identifier
            if (!isTypeStart()) return false
            advance()
            // skip generic type args on return type
            if (at(LT)) {
                advance()
                var depth = 1
                while (!eof() && depth > 0) {
                    when {
                        at(LT)        -> { depth++; advance() }
                        at(GT)        -> { depth--; advance() }
                        at(SEMICOLON) -> return false
                        else          -> advance()
                    }
                }
                if (depth != 0) return false
            }
            // skip array brackets on return type: int[]
            while (at(LBRACKET)) {
                advance()
                if (!at(RBRACKET)) return false
                advance()
            }
            // must be an identifier (function name)
            if (!at(IDENTIFIER)) return false
            advance()
            // optional generic type params on name: swap<T>
            if (at(LT)) {
                advance()
                var depth = 1
                while (!eof() && depth > 0) {
                    when {
                        at(LT)  -> { depth++; advance() }
                        at(GT)  -> { depth--; advance() }
                        else    -> advance()
                    }
                }
            }
            // must be followed by LPAREN
            return at(LPAREN)
        } finally {
            saved.rollbackTo()
        }
    }

    /**
     * Determines if current position is a variable declaration:
     *   TypeName [<>] [][] IDENTIFIER (= | ; | ,)
     */
    private fun isVarDecl(): Boolean {
        val saved = mark()
        try {
            if (!isTypeStart()) return false
            advance()
            // skip generic
            if (at(LT)) {
                advance()
                var depth = 1
                while (!eof() && depth > 0) {
                    when {
                        at(LT)        -> { depth++; advance() }
                        at(GT)        -> { depth--; advance() }
                        at(SEMICOLON) -> return false
                        else          -> advance()
                    }
                }
            }
            // skip array brackets
            while (at(LBRACKET)) {
                advance()
                if (!at(RBRACKET)) return false
                advance()
            }
            return at(IDENTIFIER)
        } finally {
            saved.rollbackTo()
        }
    }
}
