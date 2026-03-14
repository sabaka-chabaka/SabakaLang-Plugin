package com.sabakachabaka.sabakalang.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.sabakachabaka.sabakalang.SabakaFileType
import com.sabakachabaka.sabakalang.lexer.SabakaTokenTypes

object SabakaPsiFactory {

    fun createFile(project: Project, text: String): SabakaFile =
        PsiFileFactory.getInstance(project)
            .createFileFromText("dummy.sabaka", SabakaFileType.INSTANCE, text) as SabakaFile

    /** Creates an IDENTIFIER PSI element with the given name. */
    fun createIdentifier(project: Project, name: String): PsiElement {
        // Function declaration: void <name>() {}
        val file = createFile(project, "void $name() {}")
        // The second child of FUNC_DECL is the IDENTIFIER
        return file.firstChild?.let { funcDecl ->
            var child = funcDecl.firstChild
            while (child != null) {
                if (child.node.elementType == SabakaTokenTypes.IDENTIFIER) return child
                child = child.nextSibling
            }
            null
        } ?: error("Cannot create identifier '$name'")
    }
}
