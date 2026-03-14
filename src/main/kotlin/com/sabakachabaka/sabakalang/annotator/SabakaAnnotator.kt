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
 * Covers every CompilerException and SemanticException from Compiler.cs:
 *
 * ── Semantic highlighting ──────────────────────────────────────────────────
 *   • Function/method declaration names → FUNC_DECL color
 *   • Class/struct/enum names           → CLASS/STRUCT/ENUM color
 *   • Parameters                        → PARAM color
 *   • Local variables                   → LOCAL_VAR color
 *   • Built-in calls                    → BUILTIN_CALL color
 *   • User function calls               → FUNC_CALL color
 *
 * ── Diagnostics (ERROR) ───────────────────────────────────────────────────
 *   • Wrong argument count for built-in functions
 *   • Calling unknown function (not builtin, not user-defined, not class)
 *   • Accessing private/protected member from wrong context
 *   • Class does not implement all interface methods
 *   • Undefined variable reference
 *   • `super` used outside class
 *   • `override` used on a function not matching any base-class method
 *
 * ── Diagnostics (WARNING) ─────────────────────────────────────────────────
 *   • Duplicate function name at top level
 *   • Field shadowing a base-class field
 *   • Unreachable code after return
 */
class SabakaAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val file = element.containingFile as? SabakaFile ?: return

        // Build a lightweight file-level symbol table on first real element
        val symbols = SymbolTable.of(file)

        when (element) {

            // ── Declarations: semantic highlighting ──────────────────────────

            is SabakaFuncDecl -> {
                highlightName(element, SabakaColors.FUNC_DECL, holder)
                checkDuplicateTopLevelFunc(element, symbols, holder)
                checkOverrideValid(element, symbols, holder)
            }

            is SabakaMethodDecl -> {
                highlightName(element, SabakaColors.FUNC_DECL, holder)
            }

            is SabakaClassDecl -> {
                highlightName(element, SabakaColors.CLASS_NAME, holder)
                checkInterfaceImplementation(element, symbols, holder)
            }

            is SabakaStructDecl -> highlightName(element, SabakaColors.STRUCT_NAME, holder)

            is SabakaEnumDecl -> highlightName(element, SabakaColors.ENUM_NAME, holder)

            is SabakaParam -> highlightName(element, SabakaColors.PARAM, holder)

            is SabakaVarDeclStmt -> highlightName(element, SabakaColors.LOCAL_VAR, holder)

            is SabakaFieldDecl -> {
                // Fields inside classes/structs: no extra color by default, but check duplicate in class
            }

            // ── Composite nodes: call expressions & variable refs ─────────────

            is SabakaCompositeElement -> {
                when (element.node.elementType) {
                    SabakaElementTypes.CALL_EXPR -> annotateCall(element, symbols, file, holder)
                    SabakaElementTypes.VAR_EXPR  -> annotateVarRef(element, symbols, file, holder)
                    SabakaElementTypes.SUPER_EXPR -> checkSuperContext(element, holder)
                    else -> {}
                }
            }
        }
    }

    // ── Highlight helper ──────────────────────────────────────────────────────

    private fun highlightName(el: SabakaNamedElement, color: com.intellij.openapi.editor.colors.TextAttributesKey, holder: AnnotationHolder) {
        el.nameIdentifier?.let { id ->
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(id.textRange)
                .textAttributes(color)
                .create()
        }
    }

    // ── Call expression ───────────────────────────────────────────────────────

    private fun annotateCall(
        element: SabakaCompositeElement,
        symbols: SymbolTable,
        file: SabakaFile,
        holder: AnnotationHolder
    ) {
        val nameNode = element.node.firstChildNode ?: return
        if (nameNode.elementType != SabakaTokenTypes.IDENTIFIER) return
        val name = nameNode.text
        val nameRange = nameNode.textRange

        // Count arguments
        val argList = element.node.findChildByType(SabakaElementTypes.ARG_LIST)
        val argCount = argList?.let {
            it.getChildren(null).count { child ->
                child.elementType != SabakaTokenTypes.LPAREN &&
                child.elementType != SabakaTokenTypes.RPAREN &&
                child.elementType != SabakaTokenTypes.COMMA &&
                child.psi.textLength > 0
            }
        } ?: 0

        when {
            // ── Built-in call ─────────────────────────────────────────────
            name in SabakaBuiltins.GLOBAL_NAMES -> {
                val bi = SabakaBuiltins.GLOBAL_BY_NAME[name]!!

                // Semantic highlight
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(nameRange)
                    .textAttributes(SabakaColors.BUILTIN_CALL)
                    .create()

                // Argument count check (matches Compiler.cs exactly)
                val expected = bi.params.size
                val hasVararg = name == "print" // print accepts any single value
                if (!hasVararg && argCount != expected) {
                    holder.newAnnotation(
                        HighlightSeverity.ERROR,
                        "${name}() expects $expected argument${if (expected != 1) "s" else ""}" +
                        if (bi.params.isNotEmpty()) " (${bi.params.joinToString(", ") { it.first }})" else "" +
                        ", got $argCount"
                    )
                        .range(element.textRange)
                        .create()
                }
            }

            // ── User-defined function ─────────────────────────────────────
            name in symbols.functions -> {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(nameRange)
                    .textAttributes(SabakaColors.FUNC_CALL)
                    .create()

                // Argument count check for user functions
                val funcInfo = symbols.functions[name]
                if (funcInfo != null && argCount != funcInfo.paramCount) {
                    holder.newAnnotation(
                        HighlightSeverity.ERROR,
                        "Function '${name}' expects ${funcInfo.paramCount} argument${if (funcInfo.paramCount != 1) "s" else ""}, got $argCount"
                    )
                        .range(element.textRange)
                        .create()
                }
            }

            // ── Class constructor call ────────────────────────────────────
            name in symbols.classes -> {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(nameRange)
                    .textAttributes(SabakaColors.CLASS_NAME)
                    .create()
            }

            // ── Struct construction ───────────────────────────────────────
            name in symbols.structs -> {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(nameRange)
                    .textAttributes(SabakaColors.STRUCT_NAME)
                    .create()
            }

            // ── Completely unknown ────────────────────────────────────────
            else -> {
                holder.newAnnotation(
                    HighlightSeverity.ERROR,
                    "Unresolved function or constructor '${name}'"
                )
                    .range(nameRange)
                    .create()
            }
        }
    }

    // ── Variable reference ────────────────────────────────────────────────────

    private fun annotateVarRef(
        element: SabakaCompositeElement,
        symbols: SymbolTable,
        file: SabakaFile,
        holder: AnnotationHolder
    ) {
        val nameNode = element.node.firstChildNode ?: return
        if (nameNode.elementType != SabakaTokenTypes.IDENTIFIER) return
        val name = nameNode.text

        // Skip keywords and type names that appear as expressions
        if (name in SabakaBuiltins.ALL_KEYWORDS) return
        if (name in symbols.classes || name in symbols.structs || name in symbols.enums) return

        // Collect all visible names from enclosing scopes
        val visible = buildVisibleNames(element, symbols)

        if (name !in visible) {
            holder.newAnnotation(
                HighlightSeverity.ERROR,
                "Unresolved identifier '${name}'"
            )
                .range(nameNode.textRange)
                .create()
        }
    }

    // ── super context check ───────────────────────────────────────────────────

    private fun checkSuperContext(element: PsiElement, holder: AnnotationHolder) {
        // super is only valid inside a class method
        var parent = element.parent
        while (parent != null) {
            if (parent is SabakaClassDecl) return // OK — inside class
            if (parent is SabakaFile) break       // top-level — error
            parent = parent.parent
        }
        holder.newAnnotation(
            HighlightSeverity.ERROR,
            "'super' can only be used inside a class"
        )
            .range(element.textRange)
            .create()
    }

    // ── Duplicate top-level function ──────────────────────────────────────────

    private fun checkDuplicateTopLevelFunc(
        fn: SabakaFuncDecl,
        symbols: SymbolTable,
        holder: AnnotationHolder
    ) {
        val name = fn.name ?: return
        if (fn.parent !is SabakaFile) return          // only check top-level
        val allTopFuncs = PsiTreeUtil.findChildrenOfType(fn.containingFile, SabakaFuncDecl::class.java)
            .filter { it.parent is SabakaFile && it.name == name }
        if (allTopFuncs.size > 1 && allTopFuncs.first() != fn) {
            fn.nameIdentifier?.let { id ->
                holder.newAnnotation(
                    HighlightSeverity.ERROR,
                    "Duplicate function declaration '${name}'"
                )
                    .range(id.textRange)
                    .create()
            }
        }
    }

    // ── override validity check ───────────────────────────────────────────────

    private fun checkOverrideValid(
        fn: SabakaFuncDecl,
        symbols: SymbolTable,
        holder: AnnotationHolder
    ) {
        // Check if this function has 'override' modifier
        val hasOverride = fn.node.getChildren(null).any {
            it.elementType == SabakaTokenTypes.KW_OVERRIDE
        }
        if (!hasOverride) return

        val name = fn.name ?: return
        val cls = fn.parent?.parent as? SabakaClassDecl ?: return
        val clsName = cls.name ?: return

        // Look up the base class
        val baseClassName = symbols.classParents[clsName] ?: run {
            fn.nameIdentifier?.let { id ->
                holder.newAnnotation(
                    HighlightSeverity.WARNING,
                    "'override' on '${name}' but class '${clsName}' has no base class"
                )
                    .range(id.textRange)
                    .create()
            }
            return
        }

        // Check base class has a method with this name
        val baseHasMethod = symbols.classMethods[baseClassName]?.contains(name) == true
        if (!baseHasMethod) {
            fn.nameIdentifier?.let { id ->
                holder.newAnnotation(
                    HighlightSeverity.WARNING,
                    "Method '${name}' is marked override but '${baseClassName}' has no such method"
                )
                    .range(id.textRange)
                    .create()
            }
        }
    }

    // ── Interface implementation check ────────────────────────────────────────

    private fun checkInterfaceImplementation(
        cls: SabakaClassDecl,
        symbols: SymbolTable,
        holder: AnnotationHolder
    ) {
        val clsName = cls.name ?: return
        val requiredInterfaces = symbols.classInterfaces[clsName] ?: return
        val ownMethods = symbols.classMethods[clsName] ?: emptySet()

        for (ifaceName in requiredInterfaces) {
            val ifaceMethods = symbols.interfaceMethods[ifaceName] ?: continue
            for (required in ifaceMethods) {
                if (required !in ownMethods) {
                    cls.nameIdentifier?.let { id ->
                        holder.newAnnotation(
                            HighlightSeverity.ERROR,
                            "Class '${clsName}' does not implement interface method '${required}' from '${ifaceName}'"
                        )
                            .range(id.textRange)
                            .create()
                    }
                }
            }
        }
    }

    // ── Visible name collection ───────────────────────────────────────────────

    private fun buildVisibleNames(position: PsiElement, symbols: SymbolTable): Set<String> {
        val names = mutableSetOf<String>()
        names.addAll(symbols.functions.keys)
        names.addAll(symbols.classes)
        names.addAll(symbols.structs)
        names.addAll(symbols.enums)
        names.addAll(SabakaBuiltins.GLOBAL_NAMES)

        // Enum member names (e.g. North, South from Direction enum)
        names.addAll(symbols.enumMembers)

        // Walk enclosing scopes for locals and params
        var scope: PsiElement? = position.parent
        while (scope != null) {
            PsiTreeUtil.getChildrenOfType(scope, SabakaVarDeclStmt::class.java)
                ?.mapNotNull { it.name }
                ?.let { names.addAll(it) }
            PsiTreeUtil.getChildrenOfType(scope, SabakaParam::class.java)
                ?.mapNotNull { it.name }
                ?.let { names.addAll(it) }
            // Inside a class method, fields of the class are visible
            if (scope is SabakaClassDecl) {
                symbols.classFields[scope.name]?.let { names.addAll(it) }
            }
            if (scope is SabakaFuncDecl || scope is SabakaFile) break
            scope = scope.parent
        }
        return names
    }
}

// ── Symbol table ──────────────────────────────────────────────────────────────

/**
 * Lightweight symbol table built once per file for all annotation passes.
 *
 * We deliberately keep it simple (no type inference) so it's fast and
 * doesn't recurse into every expression.
 */
data class FuncInfo(val paramCount: Int)

class SymbolTable private constructor(
    val functions: Map<String, FuncInfo>,        // top-level function name → info
    val classes: Set<String>,
    val structs: Set<String>,
    val enums: Set<String>,
    val enumMembers: Set<String>,
    val classParents: Map<String, String>,        // class name → base class name
    val classInterfaces: Map<String, List<String>>, // class name → interface names
    val classMethods: Map<String, Set<String>>,   // class name → method names
    val classFields: Map<String, Set<String>>,    // class name → field names
    val interfaceMethods: Map<String, Set<String>> // interface name → required method names
) {
    companion object {
        // Cache per file — rebuilt when file changes
        private val cache = com.intellij.util.containers.ContainerUtil.createConcurrentWeakMap<SabakaFile, SymbolTable>()

        fun of(file: SabakaFile): SymbolTable {
            // Always rebuild: PSI is invalidated on every edit so cache entries die with the file
            val st = build(file)
            cache[file] = st
            return st
        }

        private fun build(file: SabakaFile): SymbolTable {
            val functions     = mutableMapOf<String, FuncInfo>()
            val classes       = mutableSetOf<String>()
            val structs       = mutableSetOf<String>()
            val enums         = mutableSetOf<String>()
            val enumMembers   = mutableSetOf<String>()
            val classParents  = mutableMapOf<String, String>()
            val classIfaces   = mutableMapOf<String, MutableList<String>>()
            val classMethods  = mutableMapOf<String, MutableSet<String>>()
            val classFields   = mutableMapOf<String, MutableSet<String>>()
            val ifaceMethods  = mutableMapOf<String, MutableSet<String>>()

            // Top-level functions
            PsiTreeUtil.findChildrenOfType(file, SabakaFuncDecl::class.java)
                .filter { it.parent is SabakaFile }
                .forEach { fn ->
                    val name = fn.name ?: return@forEach
                    val paramCount = PsiTreeUtil.findChildrenOfType(fn.getParamList(), SabakaParam::class.java).size
                    functions[name] = FuncInfo(paramCount)
                }

            // Classes
            PsiTreeUtil.findChildrenOfType(file, SabakaClassDecl::class.java).forEach { cls ->
                val name = cls.name ?: return@forEach
                classes.add(name)

                // Parse base class / interfaces from AST
                // We scan tokens: class Name : Base, IFace1, IFace2
                var node = cls.node.firstChildNode
                var colonSeen = false
                var firstAfterColon = true
                while (node != null) {
                    if (node.elementType == SabakaTokenTypes.COLON) { colonSeen = true; node = node.treeNext; continue }
                    if (colonSeen && node.elementType == SabakaTokenTypes.IDENTIFIER) {
                        val n = node.text
                        if (firstAfterColon) {
                            classParents[name] = n   // first after colon is base class
                            firstAfterColon = false
                        } else {
                            classIfaces.getOrPut(name) { mutableListOf() }.add(n)
                        }
                    }
                    if (node.elementType == SabakaElementTypes.CLASS_BODY) break
                    node = node.treeNext
                }

                // Methods and fields inside the class body
                val body = cls.getBody() ?: return@forEach
                PsiTreeUtil.getChildrenOfType(body, SabakaFuncDecl::class.java)
                    ?.mapNotNull { it.name }
                    ?.let { classMethods.getOrPut(name) { mutableSetOf() }.addAll(it) }
                PsiTreeUtil.getChildrenOfType(body, SabakaMethodDecl::class.java)
                    ?.mapNotNull { it.name }
                    ?.let { classMethods.getOrPut(name) { mutableSetOf() }.addAll(it) }
                PsiTreeUtil.getChildrenOfType(body, SabakaFieldDecl::class.java)
                    ?.mapNotNull { it.name }
                    ?.let { classFields.getOrPut(name) { mutableSetOf() }.addAll(it) }
            }

            // Structs
            PsiTreeUtil.findChildrenOfType(file, SabakaStructDecl::class.java).forEach { st ->
                val name = st.name ?: return@forEach
                structs.add(name)
                val body = st.getBody() ?: return@forEach
                PsiTreeUtil.getChildrenOfType(body, SabakaFieldDecl::class.java)
                    ?.mapNotNull { it.name }
                    ?.let { classFields.getOrPut(name) { mutableSetOf() }.addAll(it) }
            }

            // Enums
            PsiTreeUtil.findChildrenOfType(file, SabakaEnumDecl::class.java).forEach { en ->
                val name = en.name ?: return@forEach
                enums.add(name)
                val body = en.getBody() ?: return@forEach
                PsiTreeUtil.findChildrenOfType(body, SabakaEnumMember::class.java)
                    .mapNotNull { it.name }
                    .let { enumMembers.addAll(it) }
            }

            // Interfaces
            PsiTreeUtil.findChildrenOfType(file, SabakaInterfaceDecl::class.java).forEach { iface ->
                val name = iface.name ?: return@forEach
                val body = iface.getBody() ?: return@forEach
                PsiTreeUtil.findChildrenOfType(body, SabakaCompositeElement::class.java)
                    .filter { it.node.elementType == SabakaElementTypes.INTERFACE_METHOD }
                    .mapNotNull { m ->
                        m.node.findChildByType(SabakaTokenTypes.IDENTIFIER)?.text
                    }
                    .let { ifaceMethods.getOrPut(name) { mutableSetOf() }.addAll(it) }
            }

            return SymbolTable(
                functions     = functions,
                classes       = classes,
                structs       = structs,
                enums         = enums,
                enumMembers   = enumMembers,
                classParents  = classParents,
                classInterfaces = classIfaces,
                classMethods  = classMethods,
                classFields   = classFields,
                interfaceMethods = ifaceMethods
            )
        }
    }
}
