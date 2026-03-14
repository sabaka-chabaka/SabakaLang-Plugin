package com.sabakachabaka.sabakalang.run

import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.sabakachabaka.sabakalang.lexer.SabakaTokenTypes
import com.sabakachabaka.sabakalang.psi.SabakaFile
import com.sabakachabaka.sabakalang.psi.SabakaFuncDecl

/**
 * Puts a ▶ gutter icon next to the IDENTIFIER token "main"
 * when it is the name of a top-level function in a .sabaka file.
 *
 * In SabakaLang the entry point looks like:
 *     void main() { ... }
 */
class SabakaRunLineMarkerContributor : RunLineMarkerContributor() {

    override fun getInfo(element: PsiElement): Info? {
        // We want the IDENTIFIER leaf token, not composite nodes
        if (element.node.elementType != SabakaTokenTypes.IDENTIFIER) return null
        if (element.text != "main") return null

        // Parent must be FUNC_DECL directly under file (top-level)
        val func = element.parent as? SabakaFuncDecl ?: return null
        if (func.parent !is SabakaFile) return null

        return Info(
            AllIcons.RunConfigurations.TestState.Run,
            { "Run ${element.containingFile.name}" },
            *ExecutorAction.getActions()
        )
    }
}
