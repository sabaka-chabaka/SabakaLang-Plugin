package com.sabakachabaka.sabakalang.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.sabakachabaka.sabakalang.SabakaBuiltins
import com.sabakachabaka.sabakalang.SabakaLanguage
import com.sabakachabaka.sabakalang.annotator.SymbolTable
import com.sabakachabaka.sabakalang.lexer.SabakaTokenTypes
import com.sabakachabaka.sabakalang.psi.*

class SabakaCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().withLanguage(SabakaLanguage),
            SabakaMainCompletionProvider()
        )
    }
}

class SabakaMainCompletionProvider : CompletionProvider<CompletionParameters>() {

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val pos  = parameters.position
        val file = pos.containingFile as? SabakaFile ?: return

        // After dot → member completions only
        val prev = pos.prevSibling ?: pos.parent?.prevSibling
        if (prev?.node?.elementType == SabakaTokenTypes.DOT) {
            addMemberCompletions(file, result)
            return
        }

        val symbols = SymbolTable.of(file)

        // ── Keywords (no `func`!) ─────────────────────────────────────────────
        for (kw in SabakaBuiltins.ALL_KEYWORDS) {
            result.addElement(
                LookupElementBuilder.create(kw)
                    .withIcon(AllIcons.Nodes.Favorite)
                    .withBoldness(true)
                    .withTypeText("keyword")
            )
        }

        // ── Built-in functions ────────────────────────────────────────────────
        for (bi in SabakaBuiltins.GLOBAL_FUNCTIONS) {
            val tail = bi.params.joinToString(", ", "(", ")") { (n, t) -> "$t $n" }
            result.addElement(
                LookupElementBuilder.create(bi.name)
                    .withIcon(AllIcons.Nodes.Function)
                    .withTypeText(bi.returnType)
                    .withTailText(tail, true)
                    .withInsertHandler(if (bi.params.isEmpty()) EmptyParensHandler else ArgsParensHandler)
            )
        }

        // ── User-defined top-level functions ──────────────────────────────────
        PsiTreeUtil.findChildrenOfType(file, SabakaFuncDecl::class.java)
            .filter { it.parent is SabakaFile }
            .forEach { fn ->
                val name = fn.name ?: return@forEach
                val params = PsiTreeUtil.findChildrenOfType(fn.getParamList(), SabakaParam::class.java)
                val tail = params.joinToString(", ", "(", ")") { p ->
                    val typeNode = p.node.findChildByType(SabakaTokenTypes.TYPE_KEYWORDS)
                        ?: p.node.firstChildNode
                    "${typeNode?.text ?: "?"} ${p.name ?: "?"}"
                }
                result.addElement(
                    LookupElementBuilder.create(name)
                        .withIcon(AllIcons.Nodes.Function)
                        .withTypeText("function")
                        .withTailText(tail, true)
                        .withInsertHandler(ArgsParensHandler)
                )
            }

        // ── Classes ───────────────────────────────────────────────────────────
        PsiTreeUtil.findChildrenOfType(file, SabakaClassDecl::class.java).forEach { cls ->
            cls.name?.let { name ->
                result.addElement(
                    LookupElementBuilder.create(name)
                        .withIcon(AllIcons.Nodes.Class)
                        .withTypeText("class")
                )
            }
        }

        // ── Structs ───────────────────────────────────────────────────────────
        PsiTreeUtil.findChildrenOfType(file, SabakaStructDecl::class.java).forEach { st ->
            st.name?.let { name ->
                result.addElement(
                    LookupElementBuilder.create(name)
                        .withIcon(AllIcons.Nodes.Class)
                        .withTypeText("struct")
                )
            }
        }

        // ── Enums + members ───────────────────────────────────────────────────
        PsiTreeUtil.findChildrenOfType(file, SabakaEnumDecl::class.java).forEach { en ->
            en.name?.let { name ->
                result.addElement(
                    LookupElementBuilder.create(name)
                        .withIcon(AllIcons.Nodes.Enum)
                        .withTypeText("enum")
                )
            }
        }

        // ── Local variables / params ──────────────────────────────────────────
        collectLocals(pos).forEach { (name, type) ->
            result.addElement(
                LookupElementBuilder.create(name)
                    .withIcon(AllIcons.Nodes.Variable)
                    .withTypeText(type)
            )
        }
    }

    // ── Member completions after `.` ──────────────────────────────────────────

    private fun addMemberCompletions(file: SabakaFile, result: CompletionResultSet) {
        // .length is always available on arrays and strings
        result.addElement(
            LookupElementBuilder.create("length")
                .withIcon(AllIcons.Nodes.Property)
                .withTypeText("int")
                .withTailText(" (array/string)", true)
        )

        // Class method and field names
        PsiTreeUtil.findChildrenOfType(file, SabakaClassDecl::class.java).forEach { cls ->
            val body = cls.getBody() ?: return@forEach
            PsiTreeUtil.getChildrenOfType(body, SabakaFieldDecl::class.java)?.forEach { f ->
                f.name?.let { n ->
                    result.addElement(LookupElementBuilder.create(n)
                        .withIcon(AllIcons.Nodes.Field)
                        .withTypeText("field of ${cls.name}"))
                }
            }
            PsiTreeUtil.getChildrenOfType(body, SabakaFuncDecl::class.java)?.forEach { m ->
                m.name?.let { n ->
                    result.addElement(LookupElementBuilder.create(n)
                        .withIcon(AllIcons.Nodes.Method)
                        .withTypeText("method of ${cls.name}")
                        .withInsertHandler(ArgsParensHandler))
                }
            }
        }

        // Struct fields
        PsiTreeUtil.findChildrenOfType(file, SabakaStructDecl::class.java).forEach { st ->
            val body = st.getBody() ?: return@forEach
            PsiTreeUtil.getChildrenOfType(body, SabakaFieldDecl::class.java)?.forEach { f ->
                f.name?.let { n ->
                    result.addElement(LookupElementBuilder.create(n)
                        .withIcon(AllIcons.Nodes.Field)
                        .withTypeText("field of ${st.name}"))
                }
            }
        }
    }

    // ── Local symbol walk ─────────────────────────────────────────────────────

    private fun collectLocals(pos: PsiElement): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        var scope: PsiElement? = pos.parent
        while (scope != null) {
            PsiTreeUtil.getChildrenOfType(scope, SabakaVarDeclStmt::class.java)
                ?.forEach { v -> v.name?.let { result.add(it to "var") } }
            PsiTreeUtil.getChildrenOfType(scope, SabakaParam::class.java)
                ?.forEach { p -> p.name?.let { result.add(it to "param") } }
            if (scope is SabakaFuncDecl || scope is SabakaFile) break
            scope = scope.parent
        }
        return result.distinctBy { it.first }
    }
}

// ── Insert handlers ───────────────────────────────────────────────────────────

object ArgsParensHandler : InsertHandler<LookupElement> {
    override fun handleInsert(ctx: InsertionContext, item: LookupElement) {
        val doc = ctx.editor.document
        doc.insertString(ctx.tailOffset, "()")
        ctx.editor.caretModel.moveToOffset(ctx.tailOffset - 1)
    }
}

object EmptyParensHandler : InsertHandler<LookupElement> {
    override fun handleInsert(ctx: InsertionContext, item: LookupElement) {
        val doc = ctx.editor.document
        doc.insertString(ctx.tailOffset, "()")
        ctx.editor.caretModel.moveToOffset(ctx.tailOffset)
    }
}
