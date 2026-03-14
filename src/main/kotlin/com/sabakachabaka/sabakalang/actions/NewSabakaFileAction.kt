package com.sabakachabaka.sabakalang.actions

import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.sabakachabaka.sabakalang.SabakaFileType

class NewSabakaFileAction : CreateFileFromTemplateAction(
    "SabakaLang File",
    "Create a new .sabaka source file",
    SabakaFileType.INSTANCE.icon
) {
    override fun buildDialog(project: Project, directory: PsiDirectory, builder: CreateFileFromTemplateDialog.Builder) {
        builder
            .setTitle("New SabakaLang File")
            .addKind("SabakaLang File", SabakaFileType.INSTANCE.icon, "SabakaLang File")
    }

    override fun getActionName(directory: PsiDirectory, newName: String, templateName: String) =
        "Create SabakaLang File: $newName"
}
