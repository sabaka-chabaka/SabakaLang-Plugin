package com.sabakachabaka.sabakalang.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

class SabakaSettingsConfigurable : Configurable {

    private var interpreterField: TextFieldWithBrowseButton? = null
    private var workingDirField: TextFieldWithBrowseButton? = null

    override fun getDisplayName() = "SabakaLang"

    override fun createComponent(): JComponent {
        val interpField = TextFieldWithBrowseButton().also { interpreterField = it }
        interpField.addBrowseFolderListener(
            "Select SabakaLang Interpreter / Runtime",
            "Path to the sabaka executable",
            null,
            FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
        )

        val wdField = TextFieldWithBrowseButton().also { workingDirField = it }
        wdField.addBrowseFolderListener(
            "Default Working Directory",
            "Default directory used when running scripts without an explicit path",
            null,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
        )

        return FormBuilder.createFormBuilder()
            .addComponent(JBLabel("<html><b>SabakaLang Runtime</b></html>"))
            .addLabeledComponent("Interpreter path:", interpField)
            .addTooltip("Path to the 'sabaka' executable (e.g. C:\\sabaka\\sabaka.exe or /usr/local/bin/sabaka)")
            .addSeparator()
            .addComponent(JBLabel("<html><b>Defaults</b></html>"))
            .addLabeledComponent("Default working directory:", wdField)
            .addTooltip("Used when no working directory is set in the run configuration")
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    override fun isModified(): Boolean {
        val s = SabakaSettings.getInstance()
        return interpreterField?.text?.trim() != s.interpreterPath ||
               workingDirField?.text?.trim() != s.defaultWorkingDir
    }

    override fun apply() {
        val s = SabakaSettings.getInstance()
        s.interpreterPath    = interpreterField?.text?.trim() ?: ""
        s.defaultWorkingDir  = workingDirField?.text?.trim() ?: ""
    }

    override fun reset() {
        val s = SabakaSettings.getInstance()
        interpreterField?.text = s.interpreterPath
        workingDirField?.text  = s.defaultWorkingDir
    }

    override fun disposeUIResources() {
        interpreterField = null
        workingDirField  = null
    }
}
