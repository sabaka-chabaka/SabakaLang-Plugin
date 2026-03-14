package com.sabakachabaka.sabakalang.imports

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import com.sabakachabaka.sabakalang.SabakaFileType
import com.sabakachabaka.sabakalang.SabakaLanguage
import com.sabakachabaka.sabakalang.lexer.SabakaTokenTypes
import com.sabakachabaka.sabakalang.psi.SabakaCompositeElement
import com.sabakachabaka.sabakalang.psi.SabakaElementTypes
import com.sabakachabaka.sabakalang.psi.SabakaFile

/**
 * Provides path completion inside import string literals.
 *
 *   import "|"          → suggests .sabaka and .dll files relative to this file
 *   import "lib/|"      → prefix-filtered
 *   import "SabakaUI|"  → matches SabakaUI.dll
 */
class SabakaImportCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(SabakaTokenTypes.STRING_LITERAL)
                .withLanguage(SabakaLanguage),
            SabakaImportPathProvider()
        )
    }
}

private class SabakaImportPathProvider : CompletionProvider<CompletionParameters>() {

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val element = parameters.position
        val file = element.containingFile as? SabakaFile ?: return
        val project = file.project
        val vFile = file.virtualFile ?: return

        // Only fire inside an IMPORT_STMT
        val importStmt = element.parent?.parent
        if (importStmt !is SabakaCompositeElement) return
        if (importStmt.node.elementType != SabakaElementTypes.IMPORT_STMT) return

        val baseDir = vFile.parent ?: return

        // Typed prefix (strip quotes + IntelliJ completion dummy)
        val typedRaw = element.text
            .removeSurrounding("\"")
            .replace(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED, "")
            .replace(CompletionUtilCore.DUMMY_IDENTIFIER, "")

        val prefixedResult = result.withPrefixMatcher(typedRaw)

        // ── .sabaka files ─────────────────────────────────────────────────────
        val sabakaFiles = SabakaFileIndex.relativePathsFrom(baseDir, project)
            .filter { it != vFile.name }

        for (path in sabakaFiles) {
            prefixedResult.addElement(
                LookupElementBuilder.create(path)
                    .withIcon(SabakaFileType.INSTANCE.icon)
                    .withTypeText(".sabaka")
                    .withTailText(parentDirName(baseDir, path), true)
                    .withInsertHandler(replaceStringHandler(path))
            )
        }

        // ── .dll files ────────────────────────────────────────────────────────
        val dllFiles = findDllFiles(baseDir)
        for (dll in dllFiles) {
            val relPath = SabakaFileIndex.relativePath(baseDir, dll) ?: dll.name
            val defaultAlias = dll.nameWithoutExtension.lowercase()

            prefixedResult.addElement(
                LookupElementBuilder.create(relPath)
                    .withIcon(AllIcons.FileTypes.Archive)
                    .withTypeText(".dll  →  as $defaultAlias")
                    .withTailText("  [C# module]", true)
                    .withInsertHandler(dllInsertHandler(relPath, defaultAlias))
            )
        }

        // If nothing found locally, fall back to all project .sabaka files
        if (sabakaFiles.isEmpty()) {
            SabakaFileIndex.allSabakaFiles(project)
                .filter { it.path != vFile.path }
                .mapNotNull { SabakaFileIndex.relativePath(baseDir, it) }
                .sorted()
                .forEach { path ->
                    prefixedResult.addElement(
                        LookupElementBuilder.create(path)
                            .withIcon(SabakaFileType.INSTANCE.icon)
                            .withTypeText(".sabaka")
                            .withInsertHandler(replaceStringHandler(path))
                    )
                }
        }
    }

    /** Finds all .dll files in [dir] and its immediate subdirectories. */
    private fun findDllFiles(dir: VirtualFile): List<VirtualFile> {
        val results = mutableListOf<VirtualFile>()
        for (child in dir.children) {
            when {
                child.isDirectory -> child.children
                    .filter { it.name.endsWith(".dll", ignoreCase = true) }
                    .forEach { results.add(it) }
                child.name.endsWith(".dll", ignoreCase = true) -> results.add(child)
            }
        }
        return results
    }

    private fun parentDirName(base: VirtualFile, relPath: String): String {
        val parts = relPath.split("/", "\\")
        return if (parts.size > 1) "  (${parts.dropLast(1).joinToString("/")})" else ""
    }

    /** Insert handler for .sabaka: replaces whole string content */
    private fun replaceStringHandler(path: String) = InsertHandler<com.intellij.codeInsight.lookup.LookupElement> { ctx, _ ->
        val doc = ctx.document
        val text = doc.text
        var qStart = ctx.startOffset - 1
        while (qStart >= 0 && text[qStart] != '"') qStart--
        var qEnd = ctx.tailOffset
        while (qEnd < text.length && text[qEnd] != '"') qEnd++
        if (qStart >= 0 && qEnd < text.length) {
            doc.replaceString(qStart + 1, qEnd, path)
            ctx.editor.caretModel.moveToOffset(qStart + 1 + path.length)
        }
    }

    /** Insert handler for .dll: replaces string and appends `as alias;` */
    private fun dllInsertHandler(path: String, defaultAlias: String) =
        InsertHandler<com.intellij.codeInsight.lookup.LookupElement> { ctx, _ ->
            val doc = ctx.document
            val text = doc.text
            var qStart = ctx.startOffset - 1
            while (qStart >= 0 && text[qStart] != '"') qStart--
            var qEnd = ctx.tailOffset
            while (qEnd < text.length && text[qEnd] != '"') qEnd++
            if (qStart >= 0 && qEnd < text.length) {
                doc.replaceString(qStart + 1, qEnd, path)
                val afterClose = qStart + 1 + path.length + 1 // after closing "
                // Check if `as` is already there
                val tail = doc.text.substring(minOf(afterClose, doc.textLength)).trimStart()
                if (!tail.startsWith("as ") && !tail.startsWith(";")) {
                    doc.insertString(afterClose, " as $defaultAlias")
                    ctx.editor.caretModel.moveToOffset(afterClose + " as ".length + defaultAlias.length)
                } else {
                    ctx.editor.caretModel.moveToOffset(afterClose)
                }
            }
        }
}
