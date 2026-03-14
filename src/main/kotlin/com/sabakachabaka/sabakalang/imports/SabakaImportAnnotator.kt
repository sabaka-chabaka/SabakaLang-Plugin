package com.sabakachabaka.sabakalang.imports

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.sabakachabaka.sabakalang.lexer.SabakaTokenTypes
import com.sabakachabaka.sabakalang.psi.SabakaCompositeElement
import com.sabakachabaka.sabakalang.psi.SabakaElementTypes
import com.sabakachabaka.sabakalang.psi.SabakaFile

/**
 * Annotates import statements with errors when files can't be found.
 *
 *   import "utils.sabaka";          ← checks file exists relative to current file
 *   import "missing.sabaka";        ← ERROR: Import file not found
 *   import "SabakaUI.dll" as ui;    ← checks DLL exists, WARNING if not (DLL may be runtime-only)
 *   import "missing.dll";           ← WARNING: DLL not found (not hard error — may be deployed separately)
 */
class SabakaImportAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is SabakaCompositeElement) return
        if (element.node.elementType != SabakaElementTypes.IMPORT_STMT) return

        val file = element.containingFile as? SabakaFile ?: return
        val vFile = file.virtualFile ?: return

        val stringNode = element.node.findChildByType(SabakaTokenTypes.STRING_LITERAL) ?: return
        val importPath = stringNode.text.removeSurrounding("\"")

        if (importPath.isBlank()) {
            holder.newAnnotation(HighlightSeverity.ERROR, "Empty import path")
                .range(stringNode.textRange).create()
            return
        }

        when {
            importPath.endsWith(".sabaka", ignoreCase = true) -> {
                val resolved = SabakaFileIndex.resolve(importPath, vFile)
                if (resolved == null) {
                    holder.newAnnotation(
                        HighlightSeverity.ERROR,
                        "Import file not found: \"$importPath\""
                    ).range(stringNode.textRange).create()
                }
            }

            importPath.endsWith(".dll", ignoreCase = true) -> {
                val dir = vFile.parent
                val dllFile = dir?.findFileByRelativePath(importPath)
                if (dllFile == null) {
                    // DLL not found locally — WARNING not ERROR
                    // (DLLs may be installed next to the runtime executable, not the script)
                    holder.newAnnotation(
                        HighlightSeverity.WARNING,
                        "DLL not found locally: \"$importPath\" (will be looked up next to the SabakaLang runtime)"
                    ).range(stringNode.textRange).create()
                }
                // If found but not readable as .NET assembly — that's OK, we just show it exists
            }

            else -> {
                holder.newAnnotation(
                    HighlightSeverity.WARNING,
                    "Unknown import type: expected .sabaka or .dll"
                ).range(stringNode.textRange).create()
            }
        }
    }
}
