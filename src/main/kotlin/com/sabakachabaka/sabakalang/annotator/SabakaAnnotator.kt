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
 *   • Function/method declaration names → FUNC_DECL
 *   • Class / struct / enum names       → CLASS/STRUCT/ENUM
 *   • Parameters                        → PARAM
 *   • Local variables                   → LOCAL_VAR
 *   • Built-in function calls           → BUILTIN_CALL
 *   • User function calls               → FUNC_CALL
 *
 * ── Diagnostics (ERROR) ───────────────────────────────────────────────────────
 *   • Wrong argument count for built-in functions
 *   • Calling unknown function (not builtin, not user-defined, not class/struct)
 *   • Class does not implement all interface methods
 *   • Duplicate top-level function name
 *   • `super` used outside class
 *   • `override` without matching base-class method
 *
 * NOTE: Unresolved variable checks are intentionally omitted.
 * SabakaLang allows top-level statements, foreach vars, switch vars, and
 * generic type parameters — a full scope resolver would be needed to avoid
 * constant false positives. Function/call resolution is precise enough to be useful.
 */
class SabakaAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val file = element.containingFile as? SabakaFile ?: return
        val symbols = SymbolTable.of(file)

        when (element) {

            // ── Declaration highlights ────────────────────────────────────

            is SabakaFuncDecl -> {
                highlightName(element, SabakaColors.FUNC_DECL, holder)
                if (element.parent is SabakaFile) {
                    checkDuplicateTopLevelFunc(element, file, holder)
                }
                checkOverrideValid(element, symbols, holder)
            }

            is SabakaMethodDecl -> highlightName(element, SabakaColors.FUNC_DECL, holder)
            is SabakaClassDecl  -> {
                highlightName(element, SabakaColors.CLASS_NAME, holder)
                checkInterfaceImplementation(element, symbols, holder)
            }
            is SabakaStructDecl -> highlightName(element, SabakaColors.STRUCT_NAME, holder)
            is SabakaEnumDecl   -> highlightName(element, SabakaColors.ENUM_NAME, holder)
            is SabakaParam      -> highlightName(element, SabakaColors.PARAM, holder)
            is SabakaVarDeclStmt -> highlightName(element, SabakaColors.LOCAL_VAR, holder)

            // ── Composite nodes ───────────────────────────────────────────

            is SabakaCompositeElement -> when (element.node.elementType) {
                SabakaElementTypes.CALL_EXPR  -> annotateCall(element, symbols, holder)
                SabakaElementTypes.SUPER_EXPR -> checkSuperContext(element, holder)
                else -> {}
            }
        }
    }

    // ── Semantic highlight helpers ────────────────────────────────────────────

    private fun highlightName(
        el: SabakaNamedElement,
        color: com.intellij.openapi.editor.colors.TextAttributesKey,
        holder: AnnotationHolder
    ) {
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
        holder: AnnotationHolder
    ) {
        val nameNode = element.node.firstChildNode ?: return
        if (nameNode.elementType != SabakaTokenTypes.IDENTIFIER) return
        val name = nameNode.text
        val nameRange = nameNode.textRange

        // Count arguments (non-punctuation children of ARG_LIST)
        val argCount = element.node.findChildByType(SabakaElementTypes.ARG_LIST)
            ?.getChildren(null)
            ?.count { ch ->
                ch.elementType != SabakaTokenTypes.LPAREN &&
                ch.elementType != SabakaTokenTypes.RPAREN &&
                ch.elementType != SabakaTokenTypes.COMMA &&
                ch.psi.textLength > 0
            } ?: 0

        when {
            // ── Built-in ──────────────────────────────────────────────────
            name in SabakaBuiltins.GLOBAL_NAMES -> {
                val bi = SabakaBuiltins.GLOBAL_BY_NAME[name]!!
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(nameRange).textAttributes(SabakaColors.BUILTIN_CALL).create()

                val expected = bi.params.size
                // print() is variadic (accepts any single value, type doesn't matter)
                if (name != "print" && argCount != expected) {
                    val paramStr = if (bi.params.isNotEmpty())
                        " (${bi.params.joinToString(", ") { it.first }})" else ""
                    holder.newAnnotation(
                        HighlightSeverity.ERROR,
                        "$name() expects $expected argument${if (expected != 1) "s" else ""}$paramStr, got $argCount"
                    ).range(element.textRange).create()
                }
            }

            // ── User-defined function ─────────────────────────────────────
            name in symbols.functions -> {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(nameRange).textAttributes(SabakaColors.FUNC_CALL).create()

                val expected = symbols.functions[name]!!.paramCount
                if (argCount != expected) {
                    holder.newAnnotation(
                        HighlightSeverity.ERROR,
                        "'$name' expects $expected argument${if (expected != 1) "s" else ""}, got $argCount"
                    ).range(element.textRange).create()
                }
            }

            // ── Class / struct constructor ────────────────────────────────
            name in symbols.classes -> {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(nameRange).textAttributes(SabakaColors.CLASS_NAME).create()
            }

            name in symbols.structs -> {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(nameRange).textAttributes(SabakaColors.STRUCT_NAME).create()
            }

            // ── Unknown — ERROR only if it really looks like a call, not a ─
            // ── method call (those have a dot before them, handled by postfix)
            else -> {
                // Only flag as error if parent is not a member access (obj.foo())
                val parentType = element.node.treeParent?.elementType
                if (parentType != SabakaElementTypes.MEMBER_ACCESS_EXPR &&
                    parentType != SabakaElementTypes.CALL_EXPR) {
                    holder.newAnnotation(
                        HighlightSeverity.ERROR,
                        "Unresolved function or constructor '$name'"
                    ).range(nameRange).create()
                }
            }
        }
    }

    // ── super context ─────────────────────────────────────────────────────────

    private fun checkSuperContext(element: PsiElement, holder: AnnotationHolder) {
        var p = element.parent
        while (p != null) {
            if (p is SabakaClassDecl) return
            if (p is SabakaFile) break
            p = p.parent
        }
        holder.newAnnotation(HighlightSeverity.ERROR, "'super' can only be used inside a class")
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
        if (symbols.classMethods[baseName]?.contains(name) != true) {
            fn.nameIdentifier?.let {
                holder.newAnnotation(HighlightSeverity.WARNING,
                    "Method '$name' is marked override but '$baseName' has no such method")
                    .range(it.textRange).create()
            }
        }
    }

    // ── Interface implementation ──────────────────────────────────────────────

    private fun checkInterfaceImplementation(cls: SabakaClassDecl, symbols: SymbolTable, holder: AnnotationHolder) {
        val clsName = cls.name ?: return
        val ifaces = symbols.classInterfaces[clsName] ?: return
        val ownMethods = symbols.classMethods[clsName] ?: emptySet()
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
    val classMethods: Map<String, Set<String>>,
    val classFields: Map<String, Set<String>>,
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
            val classMethods = mutableMapOf<String, MutableSet<String>>()
            val classFields  = mutableMapOf<String, MutableSet<String>>()
            val ifaceMethods = mutableMapOf<String, MutableSet<String>>()

            PsiTreeUtil.findChildrenOfType(file, SabakaFuncDecl::class.java)
                .filter { it.parent is SabakaFile }
                .forEach { fn ->
                    val n = fn.name ?: return@forEach
                    val pc = PsiTreeUtil.findChildrenOfType(fn.getParamList(), SabakaParam::class.java).size
                    functions[n] = FuncInfo(pc)
                }

            PsiTreeUtil.findChildrenOfType(file, SabakaClassDecl::class.java).forEach { cls ->
                val n = cls.name ?: return@forEach
                classes.add(n)
                var node = cls.node.firstChildNode
                var colonSeen = false; var firstAfterColon = true
                while (node != null) {
                    if (node.elementType == SabakaTokenTypes.COLON) { colonSeen = true; node = node.treeNext; continue }
                    if (colonSeen && node.elementType == SabakaTokenTypes.IDENTIFIER) {
                        if (firstAfterColon) { classParents[n] = node.text; firstAfterColon = false }
                        else classIfaces.getOrPut(n) { mutableListOf() }.add(node.text)
                    }
                    if (node.elementType == SabakaElementTypes.CLASS_BODY) break
                    node = node.treeNext
                }
                val body = cls.getBody() ?: return@forEach
                PsiTreeUtil.getChildrenOfType(body, SabakaFuncDecl::class.java)
                    ?.mapNotNull { it.name }?.let { classMethods.getOrPut(n) { mutableSetOf() }.addAll(it) }
                PsiTreeUtil.getChildrenOfType(body, SabakaMethodDecl::class.java)
                    ?.mapNotNull { it.name }?.let { classMethods.getOrPut(n) { mutableSetOf() }.addAll(it) }
                PsiTreeUtil.getChildrenOfType(body, SabakaFieldDecl::class.java)
                    ?.mapNotNull { it.name }?.let { classFields.getOrPut(n) { mutableSetOf() }.addAll(it) }
            }

            PsiTreeUtil.findChildrenOfType(file, SabakaStructDecl::class.java).forEach { st ->
                val n = st.name ?: return@forEach; structs.add(n)
                val body = st.getBody() ?: return@forEach
                PsiTreeUtil.getChildrenOfType(body, SabakaFieldDecl::class.java)
                    ?.mapNotNull { it.name }?.let { classFields.getOrPut(n) { mutableSetOf() }.addAll(it) }
            }

            PsiTreeUtil.findChildrenOfType(file, SabakaEnumDecl::class.java).forEach { en ->
                val n = en.name ?: return@forEach; enums.add(n)
                PsiTreeUtil.findChildrenOfType(en.getBody() ?: return@forEach, SabakaEnumMember::class.java)
                    .mapNotNull { it.name }.let { enumMembers.addAll(it) }
            }

            PsiTreeUtil.findChildrenOfType(file, SabakaInterfaceDecl::class.java).forEach { iface ->
                val n = iface.name ?: return@forEach
                PsiTreeUtil.findChildrenOfType(iface.getBody() ?: return@forEach, SabakaCompositeElement::class.java)
                    .filter { it.node.elementType == SabakaElementTypes.INTERFACE_METHOD }
                    .mapNotNull { it.node.findChildByType(SabakaTokenTypes.IDENTIFIER)?.text }
                    .let { ifaceMethods.getOrPut(n) { mutableSetOf() }.addAll(it) }
            }

            return SymbolTable(functions, classes, structs, enums, enumMembers,
                classParents, classIfaces, classMethods, classFields, ifaceMethods)
        }
    }
}
