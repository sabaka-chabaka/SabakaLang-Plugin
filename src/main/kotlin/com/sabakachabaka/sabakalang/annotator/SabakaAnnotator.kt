package com.sabakachabaka.sabakalang.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.sabakachabaka.sabakalang.SabakaBuiltins
import com.sabakachabaka.sabakalang.highlighting.SabakaColors
import com.sabakachabaka.sabakalang.lexer.SabakaTokenTypes
import com.sabakachabaka.sabakalang.psi.*

/**
 * Semantic annotator for SabakaLang.
 *
 * ── Semantic highlighting ─────────────────────────────────────────────────────
 *   • Function/method decl names    → FUNC_DECL
 *   • Class / struct / enum names   → CLASS / STRUCT / ENUM color
 *   • Parameters                    → PARAM
 *   • Local variables               → LOCAL_VAR
 *   • Built-in calls                → BUILTIN_CALL
 *   • User function / method calls  → FUNC_CALL
 *
 * ── Diagnostics ───────────────────────────────────────────────────────────────
 *   • Wrong argument count for built-ins and user functions
 *   • Calling an unknown function / constructor
 *   • Accessing private member from outside the class
 *   • Accessing protected member from non-derived class
 *   • Accessing private/protected member from top-level code
 *   • Class does not implement all interface methods
 *   • Duplicate top-level function
 *   • override without matching base method
 *   • super used outside class
 *   • super::method where base class has no such method
 */
class SabakaAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val file = element.containingFile as? SabakaFile ?: return
        val symbols = SymbolTable.of(file)

        when (element) {

            // ── Declaration highlights ────────────────────────────────────

            is SabakaFuncDecl -> {
                highlightName(element, SabakaColors.FUNC_DECL, holder)
                if (element.parent is SabakaFile) checkDuplicateTopLevelFunc(element, file, holder)
                checkOverrideValid(element, symbols, holder)
            }
            is SabakaMethodDecl  -> highlightName(element, SabakaColors.FUNC_DECL, holder)
            is SabakaClassDecl   -> {
                highlightName(element, SabakaColors.CLASS_NAME, holder)
                checkInterfaceImplementation(element, symbols, holder)
            }
            is SabakaStructDecl  -> highlightName(element, SabakaColors.STRUCT_NAME, holder)
            is SabakaEnumDecl    -> highlightName(element, SabakaColors.ENUM_NAME, holder)
            is SabakaParam       -> highlightName(element, SabakaColors.PARAM, holder)
            is SabakaVarDeclStmt -> highlightName(element, SabakaColors.LOCAL_VAR, holder)

            // ── Composite nodes ───────────────────────────────────────────

            is SabakaCompositeElement -> when (element.node.elementType) {
                SabakaElementTypes.CALL_EXPR         -> annotateCall(element, symbols, holder)
                SabakaElementTypes.MEMBER_ACCESS_EXPR -> annotateMemberAccess(element, symbols, holder)
                SabakaElementTypes.SUPER_EXPR         -> checkSuperContext(element, symbols, holder)
                else -> {}
            }
        }
    }

    // ── Highlight helper ──────────────────────────────────────────────────────

    private fun highlightName(
        el: SabakaNamedElement,
        color: com.intellij.openapi.editor.colors.TextAttributesKey,
        holder: AnnotationHolder
    ) {
        el.nameIdentifier?.let {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(it.textRange).textAttributes(color).create()
        }
    }

    // ── Enclosing class name ──────────────────────────────────────────────────

    private fun enclosingClassName(element: PsiElement): String? {
        var p = element.parent
        while (p != null) {
            if (p is SabakaClassDecl) return p.name
            if (p is SabakaFile) return null
            p = p.parent
        }
        return null
    }

    // ── Call expression ───────────────────────────────────────────────────────

    private fun annotateCall(
        element: SabakaCompositeElement,
        symbols: SymbolTable,
        holder: AnnotationHolder
    ) {
        val nameNode = element.node.firstChildNode ?: return
        if (nameNode.elementType != SabakaTokenTypes.IDENTIFIER) return
        val name = nameNode.text
        val nameRange = nameNode.textRange

        val argCount = element.node.findChildByType(SabakaElementTypes.ARG_LIST)
            ?.getChildren(null)
            ?.count { ch ->
                ch.elementType != SabakaTokenTypes.LPAREN &&
                ch.elementType != SabakaTokenTypes.RPAREN &&
                ch.elementType != SabakaTokenTypes.COMMA &&
                ch.psi.textLength > 0
            } ?: 0

        // Is this a method call on an object? (parent is CALL_EXPR of a MEMBER_ACCESS_EXPR)
        // If so, skip — we handle it in annotateMemberAccess
        val parentType = element.node.treeParent?.elementType
        if (parentType == SabakaElementTypes.MEMBER_ACCESS_EXPR) return

        when {
            // ── Built-in ──────────────────────────────────────────────────
            name in SabakaBuiltins.GLOBAL_NAMES -> {
                val bi = SabakaBuiltins.GLOBAL_BY_NAME[name]!!
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(nameRange).textAttributes(SabakaColors.BUILTIN_CALL).create()
                val expected = bi.params.size
                if (name != "print" && argCount != expected) {
                    val pStr = if (bi.params.isNotEmpty())
                        " (${bi.params.joinToString(", ") { it.first }})" else ""
                    holder.newAnnotation(HighlightSeverity.ERROR,
                        "$name() expects $expected argument${if (expected != 1) "s" else ""}$pStr, got $argCount")
                        .range(element.textRange).create()
                }
            }

            // ── User-defined top-level function ───────────────────────────
            name in symbols.functions -> {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(nameRange).textAttributes(SabakaColors.FUNC_CALL).create()
                val info = symbols.functions[name]!!
                if (argCount != info.paramCount) {
                    holder.newAnnotation(HighlightSeverity.ERROR,
                        "'$name' expects ${info.paramCount} argument${if (info.paramCount != 1) "s" else ""}, got $argCount")
                        .range(element.textRange).create()
                }
            }

            // ── Class constructor ─────────────────────────────────────────
            name in symbols.classes -> {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(nameRange).textAttributes(SabakaColors.CLASS_NAME).create()
            }

            name in symbols.structs -> {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(nameRange).textAttributes(SabakaColors.STRUCT_NAME).create()
            }

            // ── Unknown ───────────────────────────────────────────────────
            else -> {
                holder.newAnnotation(HighlightSeverity.ERROR,
                    "Unresolved function or constructor '$name'")
                    .range(nameRange).create()
            }
        }
    }

    // ── Member access: obj.field / obj.method() / super::method() ────────────
    //
    //  MEMBER_ACCESS_EXPR node layout (from the parser):
    //    child[0] = object  (VAR_EXPR | SUPER_EXPR | CALL_EXPR | ...)
    //    child[1] = DOT or COLONCOLON
    //    child[2] = IDENTIFIER  (member name)
    //    child[3] = ARG_LIST    (optional, present when it's a call)

    private fun annotateMemberAccess(
        element: SabakaCompositeElement,
        symbols: SymbolTable,
        holder: AnnotationHolder
    ) {
        // MEMBER_ACCESS_EXPR structure (from parsePostfix):
        //   child[0] = object expr  (VAR_EXPR | SUPER_EXPR | MEMBER_ACCESS_EXPR | ...)
        //   child[1] = DOT or COLONCOLON
        //   child[2] = IDENTIFIER  (member name)
        //   child[3] = ARG_LIST    (optional — present when it's a call)
        val children = element.node.getChildren(null)
            .filter { it.psi.textLength > 0 }

        if (children.size < 3) return

        val objNode        = children[0]   // the object expression
        val sepNode        = children[1]   // DOT or COLONCOLON
        val memberNameNode = children.drop(2).firstOrNull {
            it.elementType == SabakaTokenTypes.IDENTIFIER
        } ?: return
        val memberName = memberNameNode.text

        if (sepNode.elementType != SabakaTokenTypes.DOT &&
            sepNode.elementType != SabakaTokenTypes.COLONCOLON) return

        val isSuper = objNode.elementType == SabakaElementTypes.SUPER_EXPR
        val currentClass = enclosingClassName(element)

        if (isSuper) {
            // super::method() — validate that base class has this method
            if (currentClass == null) {
                holder.newAnnotation(HighlightSeverity.ERROR,
                    "'super' can only be used inside a class")
                    .range(objNode.textRange).create()
                return
            }
            val baseClass = symbols.classParents[currentClass]
            if (baseClass == null) {
                holder.newAnnotation(HighlightSeverity.ERROR,
                    "Class '$currentClass' has no base class")
                    .range(objNode.textRange).create()
                return
            }
            // Check method exists on base class (including inherited)
            val methods = symbols.allMethodsOf(baseClass)
            if (memberName !in methods.map { it.name }) {
                holder.newAnnotation(HighlightSeverity.ERROR,
                    "'$baseClass' has no method '$memberName'")
                    .range(memberNameNode.textRange).create()
            }
            return
        }

        // Regular obj.member — look up the variable type
        val objName = objNode.psi.text
        val objTypeName = resolveLocalType(objName, element, symbols) ?: return

        // Check the member exists and is accessible
        val allMembers = symbols.allMembersOf(objTypeName)
        val member = allMembers.firstOrNull { it.name == memberName }

        if (member == null) {
            // Only flag if we positively know the type (not generic T etc.)
            if (objTypeName in symbols.classes || objTypeName in symbols.structs) {
                holder.newAnnotation(HighlightSeverity.ERROR,
                    "'$objTypeName' has no member '$memberName'")
                    .range(memberNameNode.textRange).create()
            }
            return
        }

        // Access modifier check (mirrors Compiler.cs CheckAccess exactly)
        when (member.access) {
            AccessLevel.PUBLIC -> { /* always OK */ }
            AccessLevel.PRIVATE -> {
                if (currentClass == null) {
                    holder.newAnnotation(HighlightSeverity.ERROR,
                        "Cannot access private ${member.kind} '$memberName' of '$objTypeName' from top-level")
                        .range(memberNameNode.textRange).create()
                } else if (currentClass != objTypeName) {
                    holder.newAnnotation(HighlightSeverity.ERROR,
                        "Cannot access private ${member.kind} '$memberName' of '$objTypeName' from '$currentClass'")
                        .range(memberNameNode.textRange).create()
                }
            }
            AccessLevel.PROTECTED -> {
                if (currentClass == null) {
                    holder.newAnnotation(HighlightSeverity.ERROR,
                        "Cannot access protected ${member.kind} '$memberName' of '$objTypeName' from top-level")
                        .range(memberNameNode.textRange).create()
                } else if (!symbols.isDerivedFrom(currentClass, objTypeName)) {
                    holder.newAnnotation(HighlightSeverity.ERROR,
                        "Cannot access protected ${member.kind} '$memberName' of '$objTypeName' from '$currentClass'")
                        .range(memberNameNode.textRange).create()
                }
            }
        }

        // Semantic highlight for method calls
        if (member.kind == "method") {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(memberNameNode.textRange).textAttributes(SabakaColors.FUNC_CALL).create()
        }
    }

    // ── Resolve type of a local variable by name ──────────────────────────────
    // Walks scopes upward looking for a VarDeclStmt or Param with matching name,
    // then extracts the type token text (first child of the node).

    /**
     * Resolve the declared type name of a variable/param called [name],
     * searching from [from] upward through all enclosing scopes including
     * top-level file scope.
     *
     * For  `a ab = new a();`  the VarDeclStmt node looks like:
     *   TYPE_REF("a")  IDENTIFIER("ab")  EQ  NEW_EXPR(...)
     * So the type is the text of the TYPE_REF child (first child of the node).
     *
     * Returns null if not found or if the type is a primitive (int/float/etc.).
     */
    private fun resolveLocalType(name: String, from: PsiElement, symbols: SymbolTable): String? {
        // Direct class/struct name used as receiver:  a.foo()  ClassName.field
        if (name in symbols.classes) return name
        if (name in symbols.structs) return name

        // Walk up scopes from the call site toward the file root
        var scope: PsiElement? = from.parent
        while (scope != null) {
            // Params declared in this scope (function/method parameters)
            PsiTreeUtil.getChildrenOfType(scope, SabakaParam::class.java)
                ?.firstOrNull { it.name == name }
                ?.let { param -> return typeTextOf(param.node) }

            // Var decls that are DIRECT children of this scope node
            // (handles: block locals, file-level top-level vars like `a ab = new a();`)
            PsiTreeUtil.getChildrenOfType(scope, SabakaVarDeclStmt::class.java)
                ?.firstOrNull { it.name == name }
                ?.let { v -> return typeTextOf(v.node) }

            // For BLOCK nodes: search recursively within this block only,
            // but NOT into nested class/struct bodies (avoid picking up field decls)
            if (scope.node?.elementType == SabakaElementTypes.BLOCK ||
                scope is SabakaFile) {
                PsiTreeUtil.findChildrenOfType(scope, SabakaVarDeclStmt::class.java)
                    .filter { v ->
                        // Exclude vars inside class/struct bodies
                        var p = v.parent
                        var insideClass = false
                        while (p != null && p != scope) {
                            if (p is SabakaClassDecl || p is SabakaStructDecl) {
                                insideClass = true; break
                            }
                            p = p.parent
                        }
                        !insideClass
                    }
                    .firstOrNull { it.name == name }
                    ?.let { v -> return typeTextOf(v.node) }
            }

            scope = scope.parent
        }
        return null
    }

    /**
     * Extract the type name from a VarDeclStmt or Param AST node.
     * Layout: TYPE_REF  IDENTIFIER  [= expr]
     * We return the text of the first child (TYPE_REF or first token of type).
     * Returns null for primitives since those can't have members.
     */
    private fun typeTextOf(node: com.intellij.lang.ASTNode): String? {
        // First child should be TYPE_REF, whose own first child is the type name token
        val typeRef = node.firstChildNode ?: return null
        val typeName = if (typeRef.elementType == SabakaElementTypes.TYPE_REF)
            typeRef.firstChildNode?.text
        else
            typeRef.text
        // Ignore primitives — they don't have class members
        return when (typeName) {
            "int", "float", "bool", "string", "void", null -> null
            else -> typeName
        }
    }

    // ── super context ─────────────────────────────────────────────────────────

    private fun checkSuperContext(
        element: PsiElement,
        symbols: SymbolTable,
        holder: AnnotationHolder
    ) {
        val currentClass = enclosingClassName(element)
        if (currentClass != null) return  // OK — inside a class

        // Only report if NOT inside a member access (super::foo handled there)
        val parentType = element.node.treeParent?.elementType
        if (parentType == SabakaElementTypes.MEMBER_ACCESS_EXPR) return

        holder.newAnnotation(HighlightSeverity.ERROR,
            "'super' can only be used inside a class")
            .range(element.textRange).create()
    }

    // ── Duplicate top-level function ──────────────────────────────────────────

    private fun checkDuplicateTopLevelFunc(fn: SabakaFuncDecl, file: SabakaFile, holder: AnnotationHolder) {
        val name = fn.name ?: return
        val dupes = PsiTreeUtil.findChildrenOfType(file, SabakaFuncDecl::class.java)
            .filter { it.parent is SabakaFile && it.name == name }
        if (dupes.size > 1 && dupes.first() != fn) {
            fn.nameIdentifier?.let {
                holder.newAnnotation(HighlightSeverity.ERROR, "Duplicate function '$name'")
                    .range(it.textRange).create()
            }
        }
    }

    // ── override validity ─────────────────────────────────────────────────────

    private fun checkOverrideValid(fn: SabakaFuncDecl, symbols: SymbolTable, holder: AnnotationHolder) {
        val hasOverride = fn.node.getChildren(null)
            .any { it.elementType == SabakaTokenTypes.KW_OVERRIDE }
        if (!hasOverride) return
        val name = fn.name ?: return
        val cls = fn.parent?.parent as? SabakaClassDecl ?: return
        val clsName = cls.name ?: return
        val baseName = symbols.classParents[clsName] ?: run {
            fn.nameIdentifier?.let {
                holder.newAnnotation(HighlightSeverity.WARNING,
                    "'override' on '$name' but '$clsName' has no base class")
                    .range(it.textRange).create()
            }; return
        }
        // Check base class (and its ancestors) has this method
        if (!symbols.hasMethodInHierarchy(baseName, name)) {
            fn.nameIdentifier?.let {
                holder.newAnnotation(HighlightSeverity.WARNING,
                    "Method '$name' is marked override but '$baseName' has no such method")
                    .range(it.textRange).create()
            }
        }
    }

    // ── Interface implementation ──────────────────────────────────────────────

    private fun checkInterfaceImplementation(
        cls: SabakaClassDecl,
        symbols: SymbolTable,
        holder: AnnotationHolder
    ) {
        val clsName = cls.name ?: return
        val ifaces = symbols.classInterfaces[clsName] ?: return
        // Collect all methods including inherited ones
        val ownMethods = symbols.allMethodsOf(clsName).map { it.name }.toSet()
        for (iface in ifaces) {
            for (required in symbols.interfaceMethods[iface] ?: emptySet()) {
                if (required !in ownMethods) {
                    cls.nameIdentifier?.let {
                        holder.newAnnotation(HighlightSeverity.ERROR,
                            "'$clsName' does not implement '$required' from '$iface'")
                            .range(it.textRange).create()
                    }
                }
            }
        }
    }
}

// ── Access level ──────────────────────────────────────────────────────────────

enum class AccessLevel { PUBLIC, PRIVATE, PROTECTED }

data class MemberInfo(
    val name: String,
    val kind: String,        // "method" | "field"
    val access: AccessLevel,
    val paramCount: Int = 0, // for methods
    val definingClass: String = ""
)

// ── Symbol table ──────────────────────────────────────────────────────────────

data class FuncInfo(val paramCount: Int)

class SymbolTable private constructor(
    val functions: Map<String, FuncInfo>,
    val classes: Set<String>,
    val structs: Set<String>,
    val enums: Set<String>,
    val enumMembers: Set<String>,
    val classParents: Map<String, String>,
    val classInterfaces: Map<String, List<String>>,
    // Direct (non-inherited) members per class
    val directMembers: Map<String, List<MemberInfo>>,
    val structFields: Map<String, List<MemberInfo>>,
    val interfaceMethods: Map<String, Set<String>>
) {
    companion object {
        fun of(file: SabakaFile): SymbolTable = build(file)

        private fun build(file: SabakaFile): SymbolTable {
            val functions    = mutableMapOf<String, FuncInfo>()
            val classes      = mutableSetOf<String>()
            val structs      = mutableSetOf<String>()
            val enums        = mutableSetOf<String>()
            val enumMembers  = mutableSetOf<String>()
            val classParents = mutableMapOf<String, String>()
            val classIfaces  = mutableMapOf<String, MutableList<String>>()
            val directMembers = mutableMapOf<String, MutableList<MemberInfo>>()
            val structFields = mutableMapOf<String, MutableList<MemberInfo>>()
            val ifaceMethods = mutableMapOf<String, MutableSet<String>>()

            // Top-level functions
            PsiTreeUtil.findChildrenOfType(file, SabakaFuncDecl::class.java)
                .filter { it.parent is SabakaFile }
                .forEach { fn ->
                    val n = fn.name ?: return@forEach
                    val pc = PsiTreeUtil.findChildrenOfType(
                        fn.getParamList(), SabakaParam::class.java).size
                    functions[n] = FuncInfo(pc)
                }

            // Classes
            PsiTreeUtil.findChildrenOfType(file, SabakaClassDecl::class.java).forEach { cls ->
                val clsName = cls.name ?: return@forEach
                classes.add(clsName)

                // Parse base class / interfaces from tokens:  class Foo : Base, IFace
                var node = cls.node.firstChildNode
                var colonSeen = false; var firstAfterColon = true
                while (node != null) {
                    if (node.elementType == SabakaTokenTypes.COLON) {
                        colonSeen = true; node = node.treeNext; continue
                    }
                    if (colonSeen && node.elementType == SabakaTokenTypes.IDENTIFIER) {
                        if (firstAfterColon) { classParents[clsName] = node.text; firstAfterColon = false }
                        else classIfaces.getOrPut(clsName) { mutableListOf() }.add(node.text)
                    }
                    if (node.elementType == SabakaElementTypes.CLASS_BODY) break
                    node = node.treeNext
                }

                val members = mutableListOf<MemberInfo>()

                // Methods (SabakaFuncDecl used for methods inside class body)
                val body = cls.getBody() ?: return@forEach
                PsiTreeUtil.getChildrenOfType(body, SabakaFuncDecl::class.java)
                    ?.forEach { fn ->
                        val n = fn.name ?: return@forEach
                        val access = readAccessOf(fn.node)
                        val pc = PsiTreeUtil.findChildrenOfType(
                            fn.getParamList(), SabakaParam::class.java).size
                        members.add(MemberInfo(n, "method", access, pc, clsName))
                    }
                PsiTreeUtil.getChildrenOfType(body, SabakaMethodDecl::class.java)
                    ?.forEach { m ->
                        val n = m.name ?: return@forEach
                        val access = readAccessOf(m.node)
                        members.add(MemberInfo(n, "method", access, 0, clsName))
                    }

                // Fields
                PsiTreeUtil.getChildrenOfType(body, SabakaFieldDecl::class.java)
                    ?.forEach { f ->
                        val n = f.name ?: return@forEach
                        val access = readAccessOf(f.node)
                        members.add(MemberInfo(n, "field", access, 0, clsName))
                    }

                directMembers[clsName] = members
            }

            // Structs
            PsiTreeUtil.findChildrenOfType(file, SabakaStructDecl::class.java).forEach { st ->
                val n = st.name ?: return@forEach; structs.add(n)
                val fields = mutableListOf<MemberInfo>()
                val body = st.getBody() ?: return@forEach
                PsiTreeUtil.getChildrenOfType(body, SabakaFieldDecl::class.java)
                    ?.forEach { f ->
                        f.name?.let { fn ->
                            fields.add(MemberInfo(fn, "field", AccessLevel.PUBLIC, 0, n))
                        }
                    }
                structFields[n] = fields
            }

            // Enums
            PsiTreeUtil.findChildrenOfType(file, SabakaEnumDecl::class.java).forEach { en ->
                val n = en.name ?: return@forEach; enums.add(n)
                PsiTreeUtil.findChildrenOfType(en.getBody() ?: return@forEach, SabakaEnumMember::class.java)
                    .mapNotNull { it.name }.let { enumMembers.addAll(it) }
            }

            // Interfaces
            PsiTreeUtil.findChildrenOfType(file, SabakaInterfaceDecl::class.java).forEach { iface ->
                val n = iface.name ?: return@forEach
                PsiTreeUtil.findChildrenOfType(
                    iface.getBody() ?: return@forEach, SabakaCompositeElement::class.java)
                    .filter { it.node.elementType == SabakaElementTypes.INTERFACE_METHOD }
                    .mapNotNull { it.node.findChildByType(SabakaTokenTypes.IDENTIFIER)?.text }
                    .let { ifaceMethods.getOrPut(n) { mutableSetOf() }.addAll(it) }
            }

            return SymbolTable(
                functions, classes, structs, enums, enumMembers,
                classParents, classIfaces, directMembers, structFields, ifaceMethods
            )
        }

        /** Read the access modifier from an ASTNode's children (first token if it's an access kw). */
        private fun readAccessOf(node: com.intellij.lang.ASTNode): AccessLevel {
            val first = node.firstChildNode?.elementType
            return when (first) {
                SabakaTokenTypes.KW_PRIVATE   -> AccessLevel.PRIVATE
                SabakaTokenTypes.KW_PROTECTED -> AccessLevel.PROTECTED
                else                          -> AccessLevel.PUBLIC
            }
        }
    }

    // ── Hierarchy helpers ─────────────────────────────────────────────────────

    /** Returns true if child == parent or child inherits from parent (transitively). */
    fun isDerivedFrom(child: String, parent: String): Boolean {
        if (child == parent) return true
        val base = classParents[child] ?: return false
        return isDerivedFrom(base, parent)
    }

    /** All methods visible on className, including inherited ones (base class first). */
    fun allMethodsOf(className: String): List<MemberInfo> {
        val result = mutableListOf<MemberInfo>()
        val seen = mutableSetOf<String>()
        var current: String? = className
        // Walk up inheritance chain: child methods shadow parent methods
        val chain = mutableListOf<String>()
        while (current != null) { chain.add(current); current = classParents[current] }
        // Process from root down so child overrides parent in `seen`
        for (cls in chain.reversed()) {
            (directMembers[cls] ?: emptyList())
                .filter { it.kind == "method" }
                .forEach { m -> if (seen.add(m.name)) result.add(m) else {
                    // replace with child's version
                    val idx = result.indexOfFirst { it.name == m.name }
                    if (idx >= 0) result[idx] = m
                }}
        }
        return result
    }

    /** All fields + methods visible on className (including inherited). */
    fun allMembersOf(className: String): List<MemberInfo> {
        if (className in structs) return structFields[className] ?: emptyList()
        val result = mutableListOf<MemberInfo>()
        val seenNames = mutableSetOf<String>()
        var current: String? = className
        val chain = mutableListOf<String>()
        while (current != null) { chain.add(current); current = classParents[current] }
        for (cls in chain.reversed()) {
            (directMembers[cls] ?: emptyList()).forEach { m ->
                if (seenNames.add(m.name)) result.add(m)
                else { val idx = result.indexOfFirst { it.name == m.name }; if (idx >= 0) result[idx] = m }
            }
        }
        return result
    }

    /** True if className or any ancestor has a method with the given name. */
    fun hasMethodInHierarchy(className: String, methodName: String): Boolean =
        allMethodsOf(className).any { it.name == methodName }
}
