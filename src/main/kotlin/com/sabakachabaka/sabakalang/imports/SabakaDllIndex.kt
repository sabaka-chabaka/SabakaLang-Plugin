package com.sabakachabaka.sabakalang.imports

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.net.URLClassLoader

/**
 * Represents a DLL imported into SabakaLang.
 *
 * DLL contract (from Compiler.cs LoadDll):
 *   - Classes with [SabakaExport] attribute OR implementing ISabakaModule
 *   - Methods with [SabakaExport("exportName")] → callable as alias.exportName()
 *   - Properties/fields with [SabakaExport("name")] → readable as alias.name
 *
 * Call patterns:
 *   import "SabakaUI.dll" as ui;      →  ui.ShowWindow()   (namespaced)
 *   import "SabakaUI.dll";            →  SabakaUI.method() (class-name prefix)
 *   import "SabakaUI.dll" from Foo;   →  Foo() directly
 */
data class DllModuleInfo(
    val dllName: String,               // "SabakaUI.dll"
    val alias: String,                 // "ui" (or class name if not aliased)
    val isNamespaced: Boolean,         // true if `as alias` was used
    val functions: List<DllFunctionInfo>,
    val variables: List<DllVariableInfo>
)

data class DllFunctionInfo(
    val exportName: String,   // name visible in SabakaLang  e.g. "showWindow"
    val paramCount: Int,
    val paramNames: List<String> = emptyList(),
    val returnTypeName: String = "void"
)

data class DllVariableInfo(
    val exportName: String,
    val typeName: String = "any"
)

object SabakaDllIndex {

    /**
     * Try to read exported symbols from a DLL file.
     * Uses URLClassLoader + reflection — only works if the DLL is a .NET assembly
     * exposed via a JVM-compatible bridge, or (more realistically) returns an empty
     * stub so the IDE at least knows the module exists and suppresses false positives.
     *
     * In practice the DLLs are .NET assemblies which the JVM can't load directly,
     * so we return a "known module" stub that suppresses unresolved errors on alias.anything().
     */
    fun readDll(dllFile: VirtualFile): DllModuleInfo? {
        if (!dllFile.exists()) return null
        val name = dllFile.name

        // We can't actually load .NET DLLs in the JVM. Return a stub that:
        //  1. Tells the annotator "this alias is a known module → don't error on alias.foo()"
        //  2. Provides the DLL name for display in completion
        return DllModuleInfo(
            dllName = name,
            alias = dllFile.nameWithoutExtension.lowercase(),
            isNamespaced = true,
            functions = emptyList(),   // unknown — suppress errors but no completions
            variables = emptyList()
        )
    }

    /**
     * Resolve all DLL imports in [file] and return module infos.
     * Each import statement like `import "SabakaUI.dll" as ui;` produces one entry.
     */
    fun resolveDllImports(file: com.sabakachabaka.sabakalang.psi.SabakaFile, project: Project): List<DllModuleInfo> {
        val vFile = file.virtualFile ?: return emptyList()
        val dir = vFile.parent ?: return emptyList()
        val results = mutableListOf<DllModuleInfo>()

        val importNodes = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(
            file, com.sabakachabaka.sabakalang.psi.SabakaCompositeElement::class.java
        ).filter { it.node.elementType == com.sabakachabaka.sabakalang.psi.SabakaElementTypes.IMPORT_STMT }

        for (node in importNodes) {
            val text = node.text

            // Extract path
            val pathMatch = Regex("""import\s+"([^"]+)"""").find(text) ?: continue
            val importPath = pathMatch.groupValues[1]
            if (!importPath.endsWith(".dll", ignoreCase = true)) continue

            // Extract alias: import "X.dll" as alias
            val aliasMatch = Regex("""\bas\s+(\w+)""").find(text)
            val explicitAlias = aliasMatch?.groupValues?.get(1)

            val dllFile = dir.findFileByRelativePath(importPath)

            val dllName = importPath.substringAfterLast('/').substringAfterLast('\\')
            val defaultAlias = dllName.removeSuffix(".dll").removeSuffix(".DLL").lowercase()
            val alias = explicitAlias?.lowercase() ?: defaultAlias

            if (dllFile != null) {
                // DLL exists — read it (stub for now, real content if accessible)
                val info = readDll(dllFile) ?: DllModuleInfo(dllName, alias, explicitAlias != null, emptyList(), emptyList())
                results.add(info.copy(alias = alias, isNamespaced = explicitAlias != null))
            } else {
                // DLL not found locally — still register the alias to suppress false positives
                results.add(DllModuleInfo(dllName, alias, explicitAlias != null, emptyList(), emptyList()))
            }
        }

        return results
    }
}
