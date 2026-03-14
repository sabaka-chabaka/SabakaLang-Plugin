package com.sabakachabaka.sabakalang.folding

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.sabakachabaka.sabakalang.psi.*

class SabakaFoldingBuilder : FoldingBuilderEx() {

    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
        val descriptors = mutableListOf<FoldingDescriptor>()

        PsiTreeUtil.findChildrenOfType(root, SabakaFuncDecl::class.java).forEach { fn ->
            fn.getBlock()?.let { addBlock(it, document, descriptors) }
        }
        PsiTreeUtil.findChildrenOfType(root, SabakaClassDecl::class.java).forEach { cls ->
            cls.getBody()?.let { addBlock(it, document, descriptors) }
        }
        PsiTreeUtil.findChildrenOfType(root, SabakaStructDecl::class.java).forEach { st ->
            st.getBody()?.let { addBlock(it, document, descriptors) }
        }
        PsiTreeUtil.findChildrenOfType(root, SabakaEnumDecl::class.java).forEach { en ->
            en.getBody()?.let { addBlock(it, document, descriptors) }
        }
        PsiTreeUtil.findChildrenOfType(root, SabakaInterfaceDecl::class.java).forEach { iface ->
            iface.getBody()?.let { addBlock(it, document, descriptors) }
        }

        // Group consecutive import statements
        if (root is SabakaFile) foldImports(root, document, descriptors)

        return descriptors.toTypedArray()
    }

    private fun addBlock(el: PsiElement, doc: Document, out: MutableList<FoldingDescriptor>) {
        val range = el.textRange
        if (range.length <= 2) return
        if (doc.getLineNumber(range.startOffset) == doc.getLineNumber(range.endOffset)) return
        out.add(FoldingDescriptor(el.node, range))
    }

    private fun foldImports(file: SabakaFile, doc: Document, out: MutableList<FoldingDescriptor>) {
        val imports = PsiTreeUtil.findChildrenOfType(file, SabakaCompositeElement::class.java)
            .filter { it.node.elementType == SabakaElementTypes.IMPORT_STMT }
        if (imports.size < 2) return

        var groupStart = imports[0]
        var groupEnd   = imports[0]
        for (i in 1 until imports.size) {
            if (directlyFollows(imports[i - 1], imports[i])) {
                groupEnd = imports[i]
            } else {
                emitImportFold(groupStart, groupEnd, doc, out)
                groupStart = imports[i]; groupEnd = imports[i]
            }
        }
        emitImportFold(groupStart, groupEnd, doc, out)
    }

    private fun directlyFollows(a: PsiElement, b: PsiElement): Boolean {
        var next = a.nextSibling
        while (next != null) {
            if (next == b) return true
            if (next !is PsiWhiteSpace) return false
            next = next.nextSibling
        }
        return false
    }

    private fun emitImportFold(start: PsiElement, end: PsiElement, doc: Document, out: MutableList<FoldingDescriptor>) {
        if (start == end) return
        val range = TextRange(start.textRange.startOffset, end.textRange.endOffset)
        if (doc.getLineNumber(range.startOffset) == doc.getLineNumber(range.endOffset)) return
        out.add(FoldingDescriptor(start.node, range, null, "import ...", false, emptySet()))
    }

    override fun getPlaceholderText(node: ASTNode): String = when (node.elementType) {
        SabakaElementTypes.BLOCK,
        SabakaElementTypes.CLASS_BODY,
        SabakaElementTypes.STRUCT_BODY,
        SabakaElementTypes.ENUM_BODY,
        SabakaElementTypes.INTERFACE_BODY -> "{...}"
        SabakaElementTypes.IMPORT_STMT    -> "import ..."
        else -> "..."
    }

    override fun isCollapsedByDefault(node: ASTNode) = false
}
