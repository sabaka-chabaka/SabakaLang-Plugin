package com.sabakachabaka.sabakalang.imports

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.sabakachabaka.sabakalang.SabakaFileType
import com.sabakachabaka.sabakalang.psi.SabakaFile

/**
 * Utility for finding .sabaka files and resolving imports.
 *
 * Import syntax (from Parser.cs):
 *   import "path/to/file.sabaka";
 *   import "file.sabaka" from Foo, Bar;       ← specific symbols
 *   import "file.sabaka" as alias;             ← namespaced
 *
 * Paths are relative to the importing file's directory.
 */
object SabakaFileIndex {

    /** All .sabaka VirtualFiles in the project. */
    fun allSabakaFiles(project: Project): Collection<VirtualFile> =
        FileTypeIndex.getFiles(SabakaFileType.INSTANCE, GlobalSearchScope.allScope(project))

    /**
     * Resolve an import path string like "utils.sabaka" or "lib/math.sabaka"
     * relative to [importingFile]'s directory.
     * Returns null if not found.
     */
    fun resolve(importPath: String, importingFile: VirtualFile): VirtualFile? {
        val dir = importingFile.parent ?: return null
        return dir.findFileByRelativePath(importPath)
    }

    /**
     * Parse an imported .sabaka file and return its SabakaFile PSI,
     * or null if the file doesn't exist / can't be parsed.
     */
    fun parsedFile(importPath: String, importingFile: VirtualFile, project: Project): SabakaFile? {
        val vf = resolve(importPath, importingFile) ?: return null
        return PsiManager.getInstance(project).findFile(vf) as? SabakaFile
    }

    /**
     * Returns all .sabaka files reachable relative to [baseDir],
     * as paths relative to [baseDir].  Used for completion suggestions.
     */
    fun relativePathsFrom(baseDir: VirtualFile, project: Project): List<String> {
        val results = mutableListOf<String>()
        val allFiles = allSabakaFiles(project)
        for (vf in allFiles) {
            val rel = relativePath(baseDir, vf) ?: continue
            results.add(rel)
        }
        return results.sorted()
    }

    /** Compute path of [target] relative to [base] directory, or null if not possible. */
    fun relativePath(base: VirtualFile, target: VirtualFile): String? {
        val basePath   = base.path.trimEnd('/')
        val targetPath = target.path
        return if (targetPath.startsWith("$basePath/"))
            targetPath.removePrefix("$basePath/")
        else null
    }

    /**
     * Extract the import path string from an IMPORT_STMT node text.
     * e.g.  `import "utils.sabaka";`  →  `utils.sabaka`
     */
    fun extractPath(importStmtText: String): String? {
        val m = Regex("""import\s+"([^"]+)"""").find(importStmtText)
        return m?.groupValues?.get(1)
    }
}
