package com.sabakachabaka.sabakalang.formatter

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.codeStyle.*
import com.intellij.psi.formatter.common.AbstractBlock
import com.intellij.psi.tree.TokenSet
import com.sabakachabaka.sabakalang.SabakaLanguage
import com.sabakachabaka.sabakalang.lexer.SabakaTokenTypes
import com.sabakachabaka.sabakalang.lexer.SabakaTokenTypes.LBRACE
import com.sabakachabaka.sabakalang.lexer.SabakaTokenTypes.RBRACE
import com.sabakachabaka.sabakalang.psi.SabakaElementTypes

class SabakaFormattingModelBuilder : FormattingModelBuilder {
    override fun createModel(ctx: FormattingContext): FormattingModel {
        val settings = ctx.codeStyleSettings
        val spacingBuilder = createSpacingBuilder(settings)
        val block = SabakaBlock(ctx.node, Wrap.createWrap(WrapType.NONE, false), null, spacingBuilder)
        return FormattingModelProvider.createFormattingModelForPsiFile(ctx.containingFile, block, settings)
    }

    private fun createSpacingBuilder(settings: CodeStyleSettings): SpacingBuilder =
        SpacingBuilder(settings, SabakaLanguage)
            // No space before ; or ,
            .before(SabakaTokenTypes.SEMICOLON).none()
            .before(SabakaTokenTypes.COMMA).none()
            // 1 space after ,
            .after(SabakaTokenTypes.COMMA).spaces(1)
            // Spaces around binary operators
            .around(SabakaTokenTypes.OPERATORS).spaces(1)
            .around(SabakaTokenTypes.EQ_SET).spaces(1)
            // Space before opening brace
            .before(LBRACE).spaces(1)
            // No space before (
            .before(SabakaTokenTypes.LPAREN).none()
            // 1 space after control-flow keywords  if/while/for/foreach/switch
            .after(SabakaTokenTypes.KW_IF).spaces(1)
            .after(SabakaTokenTypes.KW_WHILE).spaces(1)
            .after(SabakaTokenTypes.KW_FOR).spaces(1)
            .after(SabakaTokenTypes.KW_FOREACH).spaces(1)
            .after(SabakaTokenTypes.KW_SWITCH).spaces(1)
            .after(SabakaTokenTypes.KW_RETURN).spaces(1)
            .after(SabakaTokenTypes.KW_IN).spaces(1)
            .after(SabakaTokenTypes.KW_NEW).spaces(1)
            // No space around . and ::
            .around(SabakaTokenTypes.DOT).none()
            .around(SabakaTokenTypes.COLONCOLON).none()
            // No space inside []
            .after(SabakaTokenTypes.LBRACKET).none()
            .before(SabakaTokenTypes.RBRACKET).none()
}

private val BODY_TYPES = TokenSet.create(
    SabakaElementTypes.BLOCK,
    SabakaElementTypes.CLASS_BODY,
    SabakaElementTypes.STRUCT_BODY,
    SabakaElementTypes.ENUM_BODY,
    SabakaElementTypes.INTERFACE_BODY
)

class SabakaBlock(
    node: ASTNode,
    wrap: Wrap?,
    alignment: Alignment?,
    private val spacing: SpacingBuilder
) : AbstractBlock(node, wrap, alignment) {

    override fun buildChildren(): List<Block> {
        val children = mutableListOf<Block>()
        var child = node.firstChildNode
        while (child != null) {
            if (child.elementType != TokenType.WHITE_SPACE && child.textLength > 0) {
                children.add(SabakaBlock(child, Wrap.createWrap(WrapType.NONE, false), null, spacing))
            }
            child = child.treeNext
        }
        return children
    }

    override fun getIndent(): Indent {
        val parent = node.treeParent?.elementType ?: return Indent.getNoneIndent()
        return if (parent in BODY_TYPES &&
            node.elementType != LBRACE &&
            node.elementType != RBRACE
        ) {
            Indent.getNormalIndent()
        } else {
            Indent.getNoneIndent()
        }
    }

    override fun getChildIndent(): Indent? {
        return if (node.elementType in BODY_TYPES) Indent.getNormalIndent()
        else null
    }

    override fun getSpacing(child1: Block?, child2: Block): Spacing? =
        spacing.getSpacing(this, child1, child2)

    override fun isLeaf(): Boolean = node.firstChildNode == null

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes {
        return if (node.elementType in BODY_TYPES)
            ChildAttributes(Indent.getNormalIndent(), null)
        else
            ChildAttributes(Indent.getNoneIndent(), null)
    }
}

// ── Code style settings ───────────────────────────────────────────────────────

class SabakaCodeStyleSettings(container: CodeStyleSettings) :
    CustomCodeStyleSettings("SabakaCodeStyleSettings", container)

class SabakaCodeStyleSettingsProvider : CodeStyleSettingsProvider() {
    override fun createCustomSettings(settings: CodeStyleSettings) =
        SabakaCodeStyleSettings(settings)
    override fun getConfigurableDisplayName() = "SabakaLang"
    override fun createSettingsPage(settings: CodeStyleSettings, originalSettings: CodeStyleSettings) =
        SabakaCodeStyleConfigurable(settings, originalSettings)
}

class SabakaCodeStyleConfigurable(
    settings: CodeStyleSettings,
    cloneSettings: CodeStyleSettings
) : CodeStyleAbstractConfigurable(settings, cloneSettings, "SabakaLang") {
    override fun createPanel(settings: CodeStyleSettings): CodeStyleAbstractPanel =
        SabakaCodeStyleMainPanel(currentSettings, settings)
}

class SabakaCodeStyleMainPanel(currentSettings: CodeStyleSettings, settings: CodeStyleSettings) :
    TabbedLanguageCodeStylePanel(SabakaLanguage, currentSettings, settings) {
    override fun initTabs(settings: CodeStyleSettings) {
        addIndentOptionsTab(settings)
        addSpacesTab(settings)
    }
}

class SabakaLanguageCodeStyleSettingsProvider : LanguageCodeStyleSettingsProvider() {
    override fun getLanguage() = SabakaLanguage
    override fun getCodeSample(settingsType: SettingsType) = """
class Animal {
    private string name;
    public int age;

    public void Animal(string n, int a) {
        name = n;
        age = a;
    }

    public void speak() {
        print("I am " + name);
    }
}

int factorial(int n) {
    if (n <= 1) return 1;
    return n * factorial(n - 1);
}

void main() {
    int x = 10;
    int[] nums = [1, 2, 3];
    for (int i = 0; i < nums.length; i = i + 1) {
        print(nums[i]);
    }
}
""".trimIndent()

    override fun customizeSettings(consumer: CodeStyleSettingsCustomizable, settingsType: SettingsType) {
        when (settingsType) {
            SettingsType.SPACING_SETTINGS -> {
                consumer.showStandardOptions("SPACE_AFTER_COMMA", "SPACE_BEFORE_QUEST")
            }
            SettingsType.INDENT_SETTINGS  -> consumer.showStandardOptions("INDENT_SIZE", "TAB_SIZE", "USE_TAB_CHARACTER")
            SettingsType.WRAPPING_AND_BRACES_SETTINGS -> consumer.showStandardOptions("BRACE_STYLE")
            else -> {}
        }
    }
}
