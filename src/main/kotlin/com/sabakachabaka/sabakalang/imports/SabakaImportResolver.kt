package com.sabakachabaka.sabakalang.imports

import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.sabakachabaka.sabakalang.annotator.AccessLevel
import com.sabakachabaka.sabakalang.annotator.FuncInfo
import com.sabakachabaka.sabakalang.annotator.MemberInfo
import com.sabakachabaka.sabakalang.lexer.SabakaTokenTypes
import com.sabakachabaka.sabakalang.psi.*

/**
 * Parses all imports in a SabakaFile and returns the combined set of
 * exported symbols — functions, classes, structs, enums — that are
 * visible in the importing file.
 *
 * Only public symbols are exported (private/protected stay inside their file).
 */
object SabakaImportResolver {

    data class ImportedSymbols(
        val functions: Map<String, FuncInfo>            = emptyMap(),
        val classes: Set<String>                        = emptySet(),
        val structs: Set<String>                        = emptySet(),
        val enums: Set<String>                          = emptySet(),
        val enumMembers: Set<String>                    = emptySet(),
        val classParents: Map<String, String>           = emptyMap(),
        val classInterfaces: Map<String, List<String>>  = emptyMap(),
        val directMembers: Map<String, List<MemberInfo>>= emptyMap(),
        val structFields: Map<String, List<MemberInfo>> = emptyMap(),
        val interfaceMethods: Map<String, Set<String>>  = emptyMap(),
        // Source file paths that were successfully resolved
        val resolvedPaths: Set<String>                  = emptySet(),
        // Paths that could NOT be resolved (for diagnostics)
        val missingPaths: Set<String>                   = emptySet()
    )

    /**
     * Collect all imported symbols for [file].
     * Recursion is guarded by [visited] to prevent cycles.
     */
    fun resolve(
        file: SabakaFile,
        project: Project,
        visited: MutableSet<String> = mutableSetOf()
    ): ImportedSymbols {
        val vFile = file.virtualFile ?: return ImportedSymbols()
        val filePath = vFile.path
        if (!visited.add(filePath)) return ImportedSymbols() // cycle guard

        val functions    = mutableMapOf<String, FuncInfo>()
        val classes      = mutableSetOf<String>()
        val structs      = mutableSetOf<String>()
        val enums        = mutableSetOf<String>()
        val enumMembers  = mutableSetOf<String>()
        val classParents = mutableMapOf<String, String>()
        val classIfaces  = mutableMapOf<String, MutableList<String>>()
        val directMem    = mutableMapOf<String, MutableList<MemberInfo>>()
        val structFields = mutableMapOf<String, MutableList<MemberInfo>>()
        val ifaceMethods = mutableMapOf<String, MutableSet<String>>()
        val resolved     = mutableSetOf<String>()
        val missing      = mutableSetOf<String>()

        // Find all import statements in this file
        val importNodes = PsiTreeUtil.findChildrenOfType(file, SabakaCompositeElement::class.java)
            .filter { it.node.elementType == SabakaElementTypes.IMPORT_STMT }

        for (importNode in importNodes) {
            val importPath = SabakaFileIndex.extractPath(importNode.text) ?: continue

            val importedVFile = SabakaFileIndex.resolve(importPath, vFile)
            if (importedVFile == null) {
                missing.add(importPath)
                continue
            }
            resolved.add(importedVFile.path)

            val importedPsi = com.intellij.psi.PsiManager.getInstance(project)
                .findFile(importedVFile) as? SabakaFile ?: continue

            // Merge direct symbols from the imported file
            mergeFileSymbols(importedPsi, functions, classes, structs, enums, enumMembers,
                classParents, classIfaces, directMem, structFields, ifaceMethods)

            // Recursively resolve transitive imports
            val transitive = resolve(importedPsi, project, visited)
            functions.putAll(transitive.functions)
            classes.addAll(transitive.classes)
            structs.addAll(transitive.structs)
            enums.addAll(transitive.enums)
            enumMembers.addAll(transitive.enumMembers)
            classParents.putAll(transitive.classParents)
            transitive.classInterfaces.forEach { (k, v) ->
                classIfaces.getOrPut(k) { mutableListOf() }.addAll(v)
            }
            transitive.directMembers.forEach { (k, v) ->
                directMem.getOrPut(k) { mutableListOf() }.addAll(v)
            }
            transitive.structFields.forEach { (k, v) ->
                structFields.getOrPut(k) { mutableListOf() }.addAll(v)
            }
            transitive.interfaceMethods.forEach { (k, v) ->
                ifaceMethods.getOrPut(k) { mutableSetOf() }.addAll(v)
            }
        }

        return ImportedSymbols(
            functions, classes, structs, enums, enumMembers,
            classParents, classIfaces, directMem, structFields, ifaceMethods,
            resolved, missing
        )
    }

    private fun mergeFileSymbols(
        file: SabakaFile,
        functions: MutableMap<String, FuncInfo>,
        classes: MutableSet<String>,
        structs: MutableSet<String>,
        enums: MutableSet<String>,
        enumMembers: MutableSet<String>,
        classParents: MutableMap<String, String>,
        classIfaces: MutableMap<String, MutableList<String>>,
        directMem: MutableMap<String, MutableList<MemberInfo>>,
        structFields: MutableMap<String, MutableList<MemberInfo>>,
        ifaceMethods: MutableMap<String, MutableSet<String>>
    ) {
        // Top-level public functions
        PsiTreeUtil.findChildrenOfType(file, SabakaFuncDecl::class.java)
            .filter { it.parent is SabakaFile }
            .filter { isPublicNode(it.node) }
            .forEach { fn ->
                val n = fn.name ?: return@forEach
                val pc = PsiTreeUtil.findChildrenOfType(fn.getParamList(), SabakaParam::class.java).size
                functions[n] = FuncInfo(pc)
            }

        // Classes (only public ones exported)
        PsiTreeUtil.findChildrenOfType(file, SabakaClassDecl::class.java).forEach { cls ->
            val clsName = cls.name ?: return@forEach
            if (!isPublicNode(cls.node)) return@forEach
            classes.add(clsName)

            // Base class / interfaces
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
            val body = cls.getBody() ?: return@forEach

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

        // Structs
        PsiTreeUtil.findChildrenOfType(file, SabakaStructDecl::class.java).forEach { st ->
            val n = st.name ?: return@forEach
            if (!isPublicNode(st.node)) return@forEach
            structs.add(n)
            val fields = mutableListOf<MemberInfo>()
            PsiTreeUtil.getChildrenOfType(st.getBody() ?: return@forEach, SabakaFieldDecl::class.java)
                ?.forEach { f -> f.name?.let { fn -> fields.add(MemberInfo(fn, "field", AccessLevel.PUBLIC, 0, n)) } }
            structFields[n] = fields
        }

        // Enums
        PsiTreeUtil.findChildrenOfType(file, SabakaEnumDecl::class.java).forEach { en ->
            val n = en.name ?: return@forEach
            if (!isPublicNode(en.node)) return@forEach
            enums.add(n)
            PsiTreeUtil.findChildrenOfType(en.getBody() ?: return@forEach, SabakaEnumMember::class.java)
                .mapNotNull { it.name }.let { enumMembers.addAll(it) }
        }

        // Interfaces
        PsiTreeUtil.findChildrenOfType(file, SabakaInterfaceDecl::class.java).forEach { iface ->
            val n = iface.name ?: return@forEach
            PsiTreeUtil.findChildrenOfType(iface.getBody() ?: return@forEach, SabakaCompositeElement::class.java)
                .filter { it.node.elementType == SabakaElementTypes.INTERFACE_METHOD }
                .mapNotNull { it.node.findChildByType(SabakaTokenTypes.IDENTIFIER)?.text }
                .let { ifaceMethods.getOrPut(n) { mutableSetOf() }.addAll(it) }
        }
    }

    private fun isPublicNode(node: com.intellij.lang.ASTNode): Boolean {
        val first = node.firstChildNode?.elementType
        return first != SabakaTokenTypes.KW_PRIVATE && first != SabakaTokenTypes.KW_PROTECTED
    }

    private fun accessOf(node: com.intellij.lang.ASTNode): AccessLevel = when (node.firstChildNode?.elementType) {
        SabakaTokenTypes.KW_PRIVATE   -> AccessLevel.PRIVATE
        SabakaTokenTypes.KW_PROTECTED -> AccessLevel.PROTECTED
        else                          -> AccessLevel.PUBLIC
    }
}
