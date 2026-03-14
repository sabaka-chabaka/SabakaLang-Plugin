package com.sabakachabaka.sabakalang.structure

import com.intellij.icons.AllIcons
import com.intellij.ide.structureView.*
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.sabakachabaka.sabakalang.lexer.SabakaTokenTypes
import com.sabakachabaka.sabakalang.psi.*
import javax.swing.Icon

class SabakaStructureViewFactory : PsiStructureViewFactory {
    override fun getStructureViewBuilder(psiFile: PsiFile): StructureViewBuilder =
        object : TreeBasedStructureViewBuilder() {
            override fun createStructureViewModel(editor: Editor?): StructureViewModel =
                SabakaStructureViewModel(editor, psiFile as SabakaFile)
        }
}

class SabakaStructureViewModel(editor: Editor?, file: SabakaFile) :
    StructureViewModelBase(file, editor, SabakaFileElement(file)),
    StructureViewModel.ElementInfoProvider {
    override fun isAlwaysShowsPlus(e: StructureViewTreeElement) = false
    override fun isAlwaysLeaf(e: StructureViewTreeElement) = e is SabakaLeafElement
    override fun getSuitableClasses(): Array<Class<*>> = arrayOf(
        SabakaFuncDecl::class.java, SabakaClassDecl::class.java,
        SabakaStructDecl::class.java, SabakaEnumDecl::class.java,
        SabakaInterfaceDecl::class.java, SabakaFieldDecl::class.java,
        SabakaMethodDecl::class.java, SabakaEnumMember::class.java
    )
}

// ── File root ─────────────────────────────────────────────────────────────────

class SabakaFileElement(file: SabakaFile) : PsiTreeElementBase<SabakaFile>(file) {
    override fun getPresentableText() = element?.name ?: "?"
    override fun getIcon(open: Boolean) = AllIcons.FileTypes.Text
    override fun getChildrenBase(): Collection<StructureViewTreeElement> {
        val file = element ?: return emptyList()
        val children = mutableListOf<StructureViewTreeElement>()
        file.children.forEach { child ->
            when (child) {
                is SabakaFuncDecl      -> children.add(SabakaFuncElement(child))
                is SabakaClassDecl     -> children.add(SabakaClassElement(child))
                is SabakaStructDecl    -> children.add(SabakaStructElement(child))
                is SabakaEnumDecl      -> children.add(SabakaEnumElement(child))
                is SabakaInterfaceDecl -> children.add(SabakaIfaceElement(child))
                is SabakaCompositeElement -> {
                    when (child.node.elementType) {
                        SabakaElementTypes.FUNC_DECL      -> children.add(SabakaLeafElement(child.text.substringBefore("{").trim(), AllIcons.Nodes.Function, child))
                        SabakaElementTypes.CLASS_DECL     -> {}
                        else -> {}
                    }
                }
            }
        }
        return children
    }
}

// ── Function ──────────────────────────────────────────────────────────────────

class SabakaFuncElement(fn: SabakaFuncDecl) : PsiTreeElementBase<SabakaFuncDecl>(fn) {
    override fun getPresentableText(): String {
        val fn = element ?: return "?"
        val params = PsiTreeUtil.findChildrenOfType(fn.getParamList(), SabakaParam::class.java)
        val paramStr = params.joinToString(", ") { p ->
            val typeNode = p.node.firstChildNode
            "${typeNode?.text ?: "?"} ${p.name ?: "?"}"
        }
        return "${fn.name}($paramStr)"
    }
    override fun getIcon(open: Boolean): Icon = AllIcons.Nodes.Function
    override fun getChildrenBase(): Collection<StructureViewTreeElement> = emptyList()
}

// ── Class ─────────────────────────────────────────────────────────────────────

class SabakaClassElement(cls: SabakaClassDecl) : PsiTreeElementBase<SabakaClassDecl>(cls) {
    override fun getPresentableText() = element?.name ?: "?"
    override fun getIcon(open: Boolean): Icon = AllIcons.Nodes.Class
    override fun getChildrenBase(): Collection<StructureViewTreeElement> {
        val body = element?.getBody() ?: return emptyList()
        val children = mutableListOf<StructureViewTreeElement>()
        body.children.forEach { child ->
            when (child) {
                is SabakaFieldDecl  -> child.name?.let { children.add(SabakaLeafElement(it, AllIcons.Nodes.Field, child)) }
                is SabakaFuncDecl   -> children.add(SabakaFuncElement(child))
                is SabakaMethodDecl -> child.name?.let { children.add(SabakaLeafElement(it, AllIcons.Nodes.Method, child)) }
                else -> {}
            }
        }
        return children
    }
}

// ── Struct ────────────────────────────────────────────────────────────────────

class SabakaStructElement(st: SabakaStructDecl) : PsiTreeElementBase<SabakaStructDecl>(st) {
    override fun getPresentableText() = element?.name ?: "?"
    override fun getIcon(open: Boolean): Icon = AllIcons.Nodes.Class
    override fun getChildrenBase(): Collection<StructureViewTreeElement> {
        val body = element?.getBody() ?: return emptyList()
        return body.children.filterIsInstance<SabakaFieldDecl>()
            .mapNotNull { f -> f.name?.let { SabakaLeafElement(it, AllIcons.Nodes.Field, f) } }
    }
}

// ── Enum ──────────────────────────────────────────────────────────────────────

class SabakaEnumElement(en: SabakaEnumDecl) : PsiTreeElementBase<SabakaEnumDecl>(en) {
    override fun getPresentableText() = element?.name ?: "?"
    override fun getIcon(open: Boolean): Icon = AllIcons.Nodes.Enum
    override fun getChildrenBase(): Collection<StructureViewTreeElement> {
        val body = element?.getBody() ?: return emptyList()
        return PsiTreeUtil.findChildrenOfType(body, SabakaEnumMember::class.java)
            .mapNotNull { m -> m.name?.let { SabakaLeafElement(it, AllIcons.Nodes.EnumConstant, m) } }
    }
}

// ── Interface ─────────────────────────────────────────────────────────────────

class SabakaIfaceElement(iface: SabakaInterfaceDecl) : PsiTreeElementBase<SabakaInterfaceDecl>(iface) {
    override fun getPresentableText() = element?.name ?: "?"
    override fun getIcon(open: Boolean): Icon = AllIcons.Nodes.Interface
    override fun getChildrenBase(): Collection<StructureViewTreeElement> {
        val body = element?.getBody() ?: return emptyList()
        return PsiTreeUtil.findChildrenOfType(body, SabakaCompositeElement::class.java)
            .filter { it.node.elementType == SabakaElementTypes.INTERFACE_METHOD }
            .mapNotNull { m ->
                val name = m.node.findChildByType(SabakaTokenTypes.IDENTIFIER)?.text ?: return@mapNotNull null
                SabakaLeafElement(name, AllIcons.Nodes.AbstractMethod, m)
            }
    }
}

// ── Generic leaf ──────────────────────────────────────────────────────────────

class SabakaLeafElement(
    private val label: String,
    private val icon: Icon,
    el: com.intellij.psi.PsiElement
) : PsiTreeElementBase<com.intellij.psi.PsiElement>(el) {
    override fun getPresentableText() = label
    override fun getIcon(open: Boolean) = icon
    override fun getChildrenBase(): Collection<StructureViewTreeElement> = emptyList()
}
