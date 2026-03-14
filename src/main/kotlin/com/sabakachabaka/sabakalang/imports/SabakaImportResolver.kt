package com.sabakachabaka.sabakalang.imports

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.sabakachabaka.sabakalang.annotator.AccessLevel
import com.sabakachabaka.sabakalang.annotator.FuncInfo
import com.sabakachabaka.sabakalang.annotator.MemberInfo
import com.sabakachabaka.sabakalang.lexer.SabakaTokenTypes
import com.sabakachabaka.sabakalang.psi.*

object SabakaImportResolver {

    data class ImportedSymbols(
        val functions:        Map<String, FuncInfo>             = emptyMap(),
        val classes:          Set<String>                       = emptySet(),
        val structs:          Set<String>                       = emptySet(),
        val enums:            Set<String>                       = emptySet(),
        val enumMembers:      Set<String>                       = emptySet(),
        val classParents:     Map<String, String>               = emptyMap(),
        val classInterfaces:  Map<String, List<String>>         = emptyMap(),
        val directMembers:    Map<String, List<MemberInfo>>     = emptyMap(),
        val structFields:     Map<String, List<MemberInfo>>     = emptyMap(),
        val interfaceMethods: Map<String, Set<String>>          = emptyMap(),
        val resolvedPaths:    Set<String>                       = emptySet(),
        val missingPaths:     Set<String>                       = emptySet()
    )

    fun resolve(
        file: SabakaFile,
        project: Project,
        visited: MutableSet<String> = mutableSetOf()
    ): ImportedSymbols {
        val vFile = file.virtualFile ?: return ImportedSymbols()
        if (!visited.add(vFile.path)) return ImportedSymbols() // cycle guard

        val functions:    MutableMap<String, FuncInfo>             = mutableMapOf()
        val classes:      MutableSet<String>                       = mutableSetOf()
        val structs:      MutableSet<String>                       = mutableSetOf()
        val enums:        MutableSet<String>                       = mutableSetOf()
        val enumMembers:  MutableSet<String>                       = mutableSetOf()
        val classParents: MutableMap<String, String>               = mutableMapOf()
        val classIfaces:  MutableMap<String, MutableList<String>>  = mutableMapOf()
        val directMem:    MutableMap<String, MutableList<MemberInfo>> = mutableMapOf()
        val structFields: MutableMap<String, MutableList<MemberInfo>> = mutableMapOf()
        val ifaceMethods: MutableMap<String, MutableSet<String>>   = mutableMapOf()
        val resolved:     MutableSet<String>                       = mutableSetOf()
        val missing:      MutableSet<String>                       = mutableSetOf()

        // Walk direct children of file looking for IMPORT_STMT nodes
        // We use direct children (not findChildrenOfType) to avoid going inside class bodies
        for (child in file.children) {
            // Get the underlying ASTNode elementType
            val nodeType = child.node?.elementType ?: continue
            if (nodeType != SabakaElementTypes.IMPORT_STMT) continue

            // Extract the path from the STRING_LITERAL child of the import node
            val stringNode = child.node.findChildByType(SabakaTokenTypes.STRING_LITERAL) ?: continue
            val importPath = stringNode.text.removeSurrounding("\"").trim()
            if (importPath.isBlank() || !importPath.endsWith(".sabaka", ignoreCase = true)) continue

            // Resolve the path relative to the current file's directory
            val importedVFile = vFile.parent?.findFileByRelativePath(importPath)
            if (importedVFile == null) {
                missing.add(importPath)
                continue
            }
            resolved.add(importedVFile.path)

            val importedPsi = PsiManager.getInstance(project).findFile(importedVFile) as? SabakaFile
                ?: continue

            // Merge symbols from the imported file
            mergeFileSymbols(
                importedPsi,
                functions, classes, structs, enums, enumMembers,
                classParents, classIfaces, directMem, structFields, ifaceMethods
            )

            // Recursively pull transitive imports
            val transitive = resolve(importedPsi, project, visited)
            functions.putAll(transitive.functions)
            classes.addAll(transitive.classes)
            structs.addAll(transitive.structs)
            enums.addAll(transitive.enums)
            enumMembers.addAll(transitive.enumMembers)
            classParents.putAll(transitive.classParents)
            transitive.classInterfaces.forEach { e ->
                classIfaces.getOrPut(e.key) { mutableListOf() }.addAll(e.value)
            }
            transitive.directMembers.forEach { e ->
                directMem.getOrPut(e.key) { mutableListOf() }.addAll(e.value)
            }
            transitive.structFields.forEach { e ->
                structFields.getOrPut(e.key) { mutableListOf() }.addAll(e.value)
            }
            transitive.interfaceMethods.forEach { e ->
                ifaceMethods.getOrPut(e.key) { mutableSetOf() }.addAll(e.value)
            }
        }

        return ImportedSymbols(
            functions        = functions,
            classes          = classes,
            structs          = structs,
            enums            = enums,
            enumMembers      = enumMembers,
            classParents     = classParents,
            classInterfaces  = classIfaces,
            directMembers    = directMem,
            structFields     = structFields,
            interfaceMethods = ifaceMethods,
            resolvedPaths    = resolved,
            missingPaths     = missing
        )
    }

    // ── Merge symbols from a single file ─────────────────────────────────────

    private fun mergeFileSymbols(
        file: SabakaFile,
        functions:    MutableMap<String, FuncInfo>,
        classes:      MutableSet<String>,
        structs:      MutableSet<String>,
        enums:        MutableSet<String>,
        enumMembers:  MutableSet<String>,
        classParents: MutableMap<String, String>,
        classIfaces:  MutableMap<String, MutableList<String>>,
        directMem:    MutableMap<String, MutableList<MemberInfo>>,
        structFields: MutableMap<String, MutableList<MemberInfo>>,
        ifaceMethods: MutableMap<String, MutableSet<String>>
    ) {
        // ── Top-level public functions ────────────────────────────────────────
        for (child in file.children) {
            if (child.node?.elementType != SabakaElementTypes.FUNC_DECL) continue
            val fn = child as? SabakaFuncDecl ?: continue
            if (!isPublicNode(fn.node)) continue
            val name = fn.name ?: continue
            val pc = PsiTreeUtil.findChildrenOfType(fn.getParamList(), SabakaParam::class.java).size
            functions[name] = FuncInfo(pc)
        }

        // ── Classes ───────────────────────────────────────────────────────────
        for (child in file.children) {
            if (child.node?.elementType != SabakaElementTypes.CLASS_DECL) continue
            val cls = child as? SabakaClassDecl ?: continue
            if (!isPublicNode(cls.node)) continue
            val clsName = cls.name ?: continue
            classes.add(clsName)

            // Parse base class / interfaces:  class Foo : Base, IFace
            var node = cls.node.firstChildNode
            var colonSeen = false
            var firstAfterColon = true
            while (node != null) {
                if (node.elementType == SabakaTokenTypes.COLON) {
                    colonSeen = true; node = node.treeNext; continue
                }
                if (colonSeen && node.elementType == SabakaTokenTypes.IDENTIFIER) {
                    if (firstAfterColon) {
                        classParents[clsName] = node.text
                        firstAfterColon = false
                    } else {
                        classIfaces.getOrPut(clsName) { mutableListOf() }.add(node.text)
                    }
                }
                if (node.elementType == SabakaElementTypes.CLASS_BODY) break
                node = node.treeNext
            }

            // Members
            val members = mutableListOf<MemberInfo>()
            val body = cls.getBody() ?: continue
            PsiTreeUtil.getChildrenOfType(body, SabakaFuncDecl::class.java)?.forEach { fn ->
                val n = fn.name ?: return@forEach
                val access = accessOf(fn.node)
                val pc = PsiTreeUtil.findChildrenOfType(fn.getParamList(), SabakaParam::class.java).size
                members.add(MemberInfo(n, "method", access, pc, clsName))
            }
            PsiTreeUtil.getChildrenOfType(body, SabakaFieldDecl::class.java)?.forEach { f ->
                val n = f.name ?: return@forEach
                members.add(MemberInfo(n, "field", accessOf(f.node), 0, clsName))
            }
            directMem[clsName] = members
        }

        // ── Structs ───────────────────────────────────────────────────────────
        for (child in file.children) {
            if (child.node?.elementType != SabakaElementTypes.STRUCT_DECL) continue
            val st = child as? SabakaStructDecl ?: continue
            if (!isPublicNode(st.node)) continue
            val name = st.name ?: continue
            structs.add(name)
            val fields = mutableListOf<MemberInfo>()
            PsiTreeUtil.getChildrenOfType(st.getBody() ?: continue, SabakaFieldDecl::class.java)
                ?.forEach { f ->
                    f.name?.let { fn ->
                        fields.add(MemberInfo(fn, "field", AccessLevel.PUBLIC, 0, name))
                    }
                }
            structFields[name] = fields
        }

        // ── Enums ─────────────────────────────────────────────────────────────
        for (child in file.children) {
            if (child.node?.elementType != SabakaElementTypes.ENUM_DECL) continue
            val en = child as? SabakaEnumDecl ?: continue
            if (!isPublicNode(en.node)) continue
            val name = en.name ?: continue
            enums.add(name)
            PsiTreeUtil.findChildrenOfType(en.getBody() ?: continue, SabakaEnumMember::class.java)
                .mapNotNull { it.name }.forEach { enumMembers.add(it) }
        }

        // ── Interfaces ────────────────────────────────────────────────────────
        for (child in file.children) {
            if (child.node?.elementType != SabakaElementTypes.INTERFACE_DECL) continue
            val iface = child as? SabakaInterfaceDecl ?: continue
            val name = iface.name ?: continue
            val body = iface.getBody() ?: continue
            PsiTreeUtil.findChildrenOfType(body, SabakaCompositeElement::class.java)
                .filter { it.node.elementType == SabakaElementTypes.INTERFACE_METHOD }
                .mapNotNull { it.node.findChildByType(SabakaTokenTypes.IDENTIFIER)?.text }
                .forEach { ifaceMethods.getOrPut(name) { mutableSetOf() }.add(it) }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** A node is public unless its first child token is `private` or `protected`. */
    private fun isPublicNode(node: com.intellij.lang.ASTNode): Boolean {
        val first = node.firstChildNode?.elementType
        return first != SabakaTokenTypes.KW_PRIVATE && first != SabakaTokenTypes.KW_PROTECTED
    }

    private fun accessOf(node: com.intellij.lang.ASTNode): AccessLevel =
        when (node.firstChildNode?.elementType) {
            SabakaTokenTypes.KW_PRIVATE   -> AccessLevel.PRIVATE
            SabakaTokenTypes.KW_PROTECTED -> AccessLevel.PROTECTED
            else                          -> AccessLevel.PUBLIC
        }
}
