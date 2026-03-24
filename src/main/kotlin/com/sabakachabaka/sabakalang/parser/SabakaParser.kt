package com.sabakachabaka.sabakalang.parser

import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.psi.tree.IElementType
import com.sabakachabaka.sabakalang.lexer.SabakaTokenTypes as TT
import com.sabakachabaka.sabakalang.psi.SabakaElementTypes as ET

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
    private fun isTypeStart() = tt() in TT.ALL_TYPE_STARTS

    private fun isAccessModifier() = tt() in TT.ACCESS_MODIFIERS

    // ── File ──────────────────────────────────────────────────────────────────

    fun parseFile() {
        while (!eof()) {
            when {
                at(TT.KW_IMPORT)     -> parseImport()
                at(TT.KW_CLASS)      -> parseClass()
                at(TT.KW_STRUCT)     -> parseStruct()
                at(TT.KW_ENUM)       -> parseEnum()
                at(TT.KW_INTERFACE)  -> parseInterface()
                isAccessModifier() && peekAfterModifierIsDecl() -> parseAccessModifiedDecl()
                // Function decl or top-level var decl starts with a type token
                isTypeStart()        -> parseTopLevelTypeStart()
                // All statement keywords valid at top level too
                at(TT.KW_IF)         -> parseIfStmt()
                at(TT.KW_WHILE)      -> parseWhileStmt()
                at(TT.KW_FOR)        -> parseForStmt()
                at(TT.KW_FOREACH)    -> parseForeachStmt()
                at(TT.KW_SWITCH)     -> parseSwitchStmt()
                at(TT.KW_RETURN)     -> parseReturnStmt()
                at(TT.LBRACE)        -> parseBlock()
                // Expression statement: assignment, call, etc.
                else                 -> parseExprStmt()
            }
        }
    }

    /** Peek past optional access-modifier (and override) to see if next is class/struct/enum/interface */
    private fun peekAfterModifierIsDecl(): Boolean {
        val saved = mark()
        advance() // skip access modifier
        val result = at(TT.KW_CLASS) || at(TT.KW_STRUCT) || at(TT.KW_ENUM) || at(TT.KW_INTERFACE)
        saved.rollbackTo()
        return result
    }

    private fun parseAccessModifiedDecl() {
        advance() // consume access modifier
        when {
            at(TT.KW_CLASS)     -> parseClass()
            at(TT.KW_STRUCT)    -> parseStruct()
            at(TT.KW_ENUM)      -> parseEnum()
            at(TT.KW_INTERFACE) -> parseInterface()
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
        expect(TT.KW_IMPORT)

        // Путь всегда идёт первым: import "file.sabaka"
        if (at(TT.STRING_LITERAL)) advance()

        // Опционально: from Foo, Bar
        if (at(TT.IDENTIFIER) && text() == "from") {
            advance() // "from"
            if (at(TT.IDENTIFIER)) advance()
            while (at(TT.COMMA)) {
                advance()
                if (at(TT.IDENTIFIER)) advance()
            }
        }

        // Опционально: as alias
        if (at(TT.IDENTIFIER) && text() == "as") {
            advance() // "as"
            if (at(TT.IDENTIFIER)) advance()
        }

        consume(TT.SEMICOLON)
        m.done(ET.IMPORT_STMT)
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
        consume(TT.KW_OVERRIDE)
        // return type (may be an array type or generic, e.g. T[])
        parseTypeRef()
        // function name
        expect(TT.IDENTIFIER)
        // optional generic type params: <T>  <T, U>
        parseOptionalTypeParams()
        // parameters
        parseParamList()
        // body
        parseBlock()
        m.done(if (inClass) ET.METHOD_DECL else ET.FUNC_DECL)
    }

    private fun parseParamList() {
        val m = mark()
        expect(TT.LPAREN)
        while (!eof() && !at(TT.RPAREN)) {
            val pm = mark()
            parseTypeRef()         // param type  (e.g. int, T, string[])
            expect(TT.IDENTIFIER)     // param name
            pm.done(ET.PARAM)
            if (!consume(TT.COMMA)) break
        }
        expect(TT.RPAREN)
        m.done(ET.PARAM_LIST)
    }

    // ── Class ─────────────────────────────────────────────────────────────────

    private fun parseClass() {
        val m = mark()
        if (isAccessModifier()) advance()
        expect(TT.KW_CLASS)
        expect(TT.IDENTIFIER)
        parseOptionalTypeParams()
        // optional : BaseClass, Interface1, Interface2
        if (at(TT.COLON)) {
            advance()
            expect(TT.IDENTIFIER)
            while (consume(TT.COMMA)) expect(TT.IDENTIFIER)
        }
        val body = mark()
        expect(TT.LBRACE)
        while (!eof() && !at(TT.RBRACE)) {
            parseClassMember()
        }
        expect(TT.RBRACE)
        body.done(ET.CLASS_BODY)
        m.done(ET.CLASS_DECL)
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
        expect(TT.IDENTIFIER)
        if (at(TT.EQ)) {
            advance()
            parseExpr()
        }
        consume(TT.SEMICOLON)
        m.done(ET.FIELD_DECL)
    }

    // ── Struct ────────────────────────────────────────────────────────────────

    private fun parseStruct() {
        val m = mark()
        if (isAccessModifier()) advance()
        expect(TT.KW_STRUCT)
        expect(TT.IDENTIFIER)
        val body = mark()
        expect(TT.LBRACE)
        while (!eof() && !at(TT.RBRACE)) {
            if (isTypeStart()) {
                val fm = mark()
                parseTypeRef()
                expect(TT.IDENTIFIER)
                consume(TT.SEMICOLON)
                fm.done(ET.FIELD_DECL)
            } else {
                b.error("Expected field"); advance()
            }
        }
        expect(TT.RBRACE)
        body.done(ET.STRUCT_BODY)
        m.done(ET.STRUCT_DECL)
    }

    // ── Enum ──────────────────────────────────────────────────────────────────

    private fun parseEnum() {
        val m = mark()
        if (isAccessModifier()) advance()
        expect(TT.KW_ENUM)
        expect(TT.IDENTIFIER)
        val body = mark()
        expect(TT.LBRACE)
        while (!eof() && !at(TT.RBRACE)) {
            val em = mark()
            expect(TT.IDENTIFIER)
            consume(TT.COMMA)
            em.done(ET.ENUM_MEMBER)
        }
        expect(TT.RBRACE)
        body.done(ET.ENUM_BODY)
        m.done(ET.ENUM_DECL)
    }

    // ── Interface ─────────────────────────────────────────────────────────────
    // Interface methods have no body: ReturnType name(params);

    private fun parseInterface() {
        val m = mark()
        if (isAccessModifier()) advance()
        expect(TT.KW_INTERFACE)
        expect(TT.IDENTIFIER)
        parseOptionalTypeParams()
        // optional : Parent1, Parent2
        if (at(TT.COLON)) {
            advance()
            expect(TT.IDENTIFIER)
            while (consume(TT.COMMA)) expect(TT.IDENTIFIER)
        }
        val body = mark()
        expect(TT.LBRACE)
        while (!eof() && !at(TT.RBRACE)) {
            val mm = mark()
            if (isAccessModifier()) advance()
            parseTypeRef()         // return type
            expect(TT.IDENTIFIER)     // method name
            parseParamList()
            consume(TT.SEMICOLON)
            mm.done(ET.INTERFACE_METHOD)
        }
        expect(TT.RBRACE)
        body.done(ET.INTERFACE_BODY)
        m.done(ET.INTERFACE_DECL)
    }

    // ── Block ─────────────────────────────────────────────────────────────────

    private fun parseBlock() {
        val m = mark()
        expect(TT.LBRACE)
        while (!eof() && !at(TT.RBRACE)) parseStatement()
        expect(TT.RBRACE)
        m.done(ET.BLOCK)
    }

    // ── Statements ────────────────────────────────────────────────────────────

    private fun parseStatement() {
        when {
            isTypeStart() && isVarDecl() -> parseVarDeclStmt()
            at(TT.KW_RETURN)   -> parseReturnStmt()
            at(TT.KW_IF)       -> parseIfStmt()
            at(TT.KW_WHILE)    -> parseWhileStmt()
            at(TT.KW_FOR)      -> parseForStmt()
            at(TT.KW_FOREACH)  -> parseForeachStmt()
            at(TT.KW_SWITCH)   -> parseSwitchStmt()
            at(TT.LBRACE)      -> parseBlock()
            else            -> parseExprStmt()
        }
    }

    private fun parseVarDeclStmt() {
        val m = mark()
        parseTypeRef()
        expect(TT.IDENTIFIER)
        if (at(TT.EQ)) { advance(); parseExpr() }
        consume(TT.SEMICOLON)
        m.done(ET.VAR_DECL_STMT)
    }

    private fun parseReturnStmt() {
        val m = mark()
        expect(TT.KW_RETURN)
        if (!at(TT.SEMICOLON) && !at(TT.RBRACE) && !eof()) parseExpr()
        consume(TT.SEMICOLON)
        m.done(ET.RETURN_STMT)
    }

    private fun parseIfStmt() {
        val m = mark()
        expect(TT.KW_IF)
        expect(TT.LPAREN); parseExpr(); expect(TT.RPAREN)
        parseStatement()
        if (at(TT.KW_ELSE)) { advance(); parseStatement() }
        m.done(ET.IF_STMT)
    }

    private fun parseWhileStmt() {
        val m = mark()
        expect(TT.KW_WHILE)
        expect(TT.LPAREN); parseExpr(); expect(TT.RPAREN)
        parseStatement()
        m.done(ET.WHILE_STMT)
    }

    private fun parseForStmt() {
        val m = mark()
        expect(TT.KW_FOR)
        expect(TT.LPAREN)
        // init
        if (!at(TT.SEMICOLON)) {
            if (isTypeStart() && isVarDecl()) parseVarDeclStmt()
            else parseExprStmt()
        } else advance()
        // condition
        if (!at(TT.SEMICOLON)) parseExpr()
        consume(TT.SEMICOLON)
        // increment
        if (!at(TT.RPAREN)) parseExpr()
        expect(TT.RPAREN)
        parseStatement()
        m.done(ET.FOR_STMT)
    }

    private fun parseForeachStmt() {
        val m = mark()
        expect(TT.KW_FOREACH)
        expect(TT.LPAREN)
        parseTypeRef()
        expect(TT.IDENTIFIER)
        expect(TT.KW_IN)
        parseExpr()
        expect(TT.RPAREN)
        parseStatement()
        m.done(ET.FOREACH_STMT)
    }

    private fun parseSwitchStmt() {
        val m = mark()
        expect(TT.KW_SWITCH)
        expect(TT.LPAREN); parseExpr(); expect(TT.RPAREN)
        expect(TT.LBRACE)
        while (!eof() && !at(TT.RBRACE)) {
            val cm = mark()
            when {
                at(TT.KW_CASE) -> {
                    advance(); parseExpr(); expect(TT.COLON)
                    while (!eof() && !at(TT.KW_CASE) && !at(TT.KW_DEFAULT) && !at(TT.RBRACE))
                        parseStatement()
                    cm.done(ET.CASE_CLAUSE)
                }
                at(TT.KW_DEFAULT) -> {
                    advance(); expect(TT.COLON)
                    while (!eof() && !at(TT.KW_CASE) && !at(TT.RBRACE))
                        parseStatement()
                    cm.done(ET.CASE_CLAUSE)
                }
                else -> { b.error("Expected 'case' or 'default'"); advance(); cm.drop() }
            }
        }
        expect(TT.RBRACE)
        m.done(ET.SWITCH_STMT)
    }

    private fun parseExprStmt() {
        // Guard: skip tokens that can never start an expression to avoid infinite loops.
        // This can happen if we land here with a keyword that wasn't caught above.
        if (at(TT.RBRACE) || at(TT.RBRACKET) || at(TT.RPAREN) || eof()) return
        val m = mark()
        parseExpr()
        consume(TT.SEMICOLON)
        m.done(ET.EXPR_STMT)
    }

    // ── Expressions (Pratt-style) ─────────────────────────────────────────────

    private fun parseExpr() = parseAssign()

    private fun parseAssign() {
        val m = mark()
        parseOr()
        if (at(TT.EQ)) {
            advance(); parseAssign()
            m.done(ET.ASSIGN_STMT)
        } else m.drop()
    }

    private fun parseOr()        = parseBinary({ parseAnd() },       TT.OROR)
    private fun parseAnd()       = parseBinary({ parseEquality() },  TT.ANDAND)
    private fun parseEquality()  = parseBinary({ parseRelational() }, TT.EQEQ, TT.NEQ)
    private fun parseRelational()= parseBinary({ parseAddSub() },    TT.LT, TT.GT, TT.LTE, TT.GTE)
    private fun parseAddSub()    = parseBinary({ parseMulDiv() },     TT.PLUS, TT.MINUS)
    private fun parseMulDiv()    = parseBinary({ parseUnary() },      TT.STAR, TT.SLASH, TT.PERCENT)

    private fun parseBinary(sub: () -> Unit, vararg ops: IElementType) {
        sub()
        while (!eof() && tt() in ops) { advance(); sub() }
    }

    private fun parseUnary() {
        if (at(TT.MINUS) || at(TT.BANG)) {
            val m = mark(); advance(); parseUnary(); m.done(ET.UNARY_EXPR)
        } else {
            parsePostfix()
        }
    }

    private fun parsePostfix() {
        // PsiBuilder precede() pattern for left-recursive postfix chains.
        // We open an outer marker BEFORE parsing the primary, then after each
        // postfix suffix we close it as MEMBER_ACCESS_EXPR / ARRAY_ACCESS_EXPR
        // and immediately open a new outer marker for the next suffix.
        //
        //  ab.foo().bar[0]
        //  └─ MEMBER_ACCESS_EXPR
        //      ├─ MEMBER_ACCESS_EXPR        (ab.foo())
        //      │   ├─ VAR_EXPR(ab)
        //      │   ├─ DOT
        //      │   ├─ IDENTIFIER(foo)
        //      │   └─ ARG_LIST()
        //      ├─ DOT
        //      ├─ IDENTIFIER(bar)
        //      └─ [0]  → ARRAY_ACCESS_EXPR wraps the above

        var lhs = mark()   // open marker before primary
        parsePrimary()
        // lhs is now open and wraps the primary; we'll either keep it as-is
        // (no postfix) by dropping it, or use it to build a postfix node.

        while (true) {
            when {
                at(TT.DOT) || at(TT.COLONCOLON) -> {
                    advance() // consume . or ::
                    if (at(TT.IDENTIFIER)) advance() // member name
                    if (at(TT.LPAREN)) parseArgList()
                    lhs.done(ET.MEMBER_ACCESS_EXPR)
                    // Open new outer marker for possible further chaining
                    lhs = b.mark()
                }
                at(TT.LBRACKET) -> {
                    advance(); parseExpr(); expect(TT.RBRACKET)
                    lhs.done(ET.ARRAY_ACCESS_EXPR)
                    lhs = b.mark()
                }
                else -> {
                    // No more postfix — drop the dangling open marker
                    lhs.drop()
                    break
                }
            }
        }
    }

    private fun parsePrimary() {
        val m = mark()
        when {
            at(TT.INT_LITERAL) || at(TT.FLOAT_LITERAL) || at(TT.STRING_LITERAL) || at(TT.BOOL_LITERAL) -> {
                advance(); m.done(ET.LITERAL_EXPR)
            }
            at(TT.KW_NEW) -> {
                advance()
                expect(TT.IDENTIFIER)
                parseOptionalTypeParams()
                when {
                    at(TT.LBRACKET) -> { advance(); parseExpr(); expect(TT.RBRACKET) }
                    at(TT.LPAREN)   -> parseArgList()
                }
                m.done(ET.NEW_EXPR)
            }
            at(TT.KW_SUPER) -> { advance(); m.done(ET.SUPER_EXPR) }
            at(TT.LBRACKET) -> {
                advance()
                while (!eof() && !at(TT.RBRACKET)) {
                    parseExpr(); if (!consume(TT.COMMA)) break
                }
                expect(TT.RBRACKET)
                m.done(ET.ARRAY_LITERAL)
            }
            at(TT.IDENTIFIER) -> {
                advance()
                if (at(TT.LPAREN)) { parseArgList(); m.done(ET.CALL_EXPR) }
                else m.done(ET.VAR_EXPR)
            }
            at(TT.LPAREN) -> { advance(); parseExpr(); expect(TT.RPAREN); m.drop() }
            else -> {
                if (!eof()) { b.error("Expected expression, got '${text()}'"); advance() }
                m.drop()
            }
        }
    }

    private fun parseArgList() {
        val m = mark()
        expect(TT.LPAREN)
        while (!eof() && !at(TT.RPAREN)) {
            parseExpr(); if (!consume(TT.COMMA)) break
        }
        expect(TT.RPAREN)
        m.done(ET.ARG_LIST)
    }

    // ── Type references ───────────────────────────────────────────────────────
    //
    // Types: int, float, bool, string, void, SomeClass, T, Box<int>, int[], T[]

    private fun parseTypeRef() {
        val m = mark()
        when {
            tt() in TT.TYPE_KEYWORDS -> advance()
            at(TT.IDENTIFIER)        -> {
                advance()
                // Generic: Box<int>  or  T  alone
                if (at(TT.LT)) parseOptionalTypeParams()
            }
            else -> b.error("Expected type, got '${text()}'")
        }
        // Array suffix: []
        while (at(TT.LBRACKET) && peekIsRBracket()) {
            advance(); expect(TT.RBRACKET)
        }
        m.done(ET.TYPE_REF)
    }

    private fun peekIsRBracket(): Boolean {
        val s = mark()
        advance() // [
        val r = at(TT.RBRACKET) || at(TT.RBRACKET) // next is ] → true
        s.rollbackTo()
        // re-check after rollback
        val s2 = mark()
        advance()
        val result = at(TT.RBRACKET)
        s2.rollbackTo()
        return result
    }

    /** Parses optional <T>, <T, U> etc. */
    private fun parseOptionalTypeParams() {
        if (!at(TT.LT)) return
        val saved = mark()
        advance() // <
        var depth = 1
        while (!eof() && depth > 0) {
            when {
                at(TT.LT)        -> { depth++; advance() }
                at(TT.GT)        -> { depth--; advance() }
                at(TT.SEMICOLON) -> break
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
     *   [access] [override] TypeName [<>] [][] TT.IDENTIFIER TT.LPAREN
     */
    fun isFunctionDeclaration(): Boolean {
        val saved = mark()
        try {
            // optional access modifier
            if (isAccessModifier()) advance()
            // optional override
            if (at(TT.KW_OVERRIDE)) advance()
            // return type must be a type keyword or identifier
            if (!isTypeStart()) return false
            advance()
            // skip generic type args on return type
            if (at(TT.LT)) {
                advance()
                var depth = 1
                while (!eof() && depth > 0) {
                    when {
                        at(TT.LT)        -> { depth++; advance() }
                        at(TT.GT)        -> { depth--; advance() }
                        at(TT.SEMICOLON) -> return false
                        else          -> advance()
                    }
                }
                if (depth != 0) return false
            }
            // skip array brackets on return type: int[]
            while (at(TT.LBRACKET)) {
                advance()
                if (!at(TT.RBRACKET)) return false
                advance()
            }
            // must be an identifier (function name)
            if (!at(TT.IDENTIFIER)) return false
            advance()
            // optional generic type params on name: swap<T>
            if (at(TT.LT)) {
                advance()
                var depth = 1
                while (!eof() && depth > 0) {
                    when {
                        at(TT.LT)  -> { depth++; advance() }
                        at(TT.GT)  -> { depth--; advance() }
                        else    -> advance()
                    }
                }
            }
            // must be followed by TT.LPAREN
            return at(TT.LPAREN)
        } finally {
            saved.rollbackTo()
        }
    }

    /**
     * Determines if current position is a variable declaration:
     *   TypeName [<>] [][] TT.IDENTIFIER (= | ; | ,)
     */
    private fun isVarDecl(): Boolean {
        val saved = mark()
        try {
            if (!isTypeStart()) return false
            advance()
            // skip generic
            if (at(TT.LT)) {
                advance()
                var depth = 1
                while (!eof() && depth > 0) {
                    when {
                        at(TT.LT)        -> { depth++; advance() }
                        at(TT.GT)        -> { depth--; advance() }
                        at(TT.SEMICOLON) -> return false
                        else          -> advance()
                    }
                }
            }
            // skip array brackets
            while (at(TT.LBRACKET)) {
                advance()
                if (!at(TT.RBRACKET)) return false
                advance()
            }
            return at(TT.IDENTIFIER)
        } finally {
            saved.rollbackTo()
        }
    }
}
