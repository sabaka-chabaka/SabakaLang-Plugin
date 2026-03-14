package com.sabakachabaka.sabakalang

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.icons.AllIcons
import javax.swing.Icon

class SabakaFileType private constructor() : LanguageFileType(SabakaLanguage) {
    override fun getName() = "SabakaLang"
    override fun getDescription() = "SabakaLang source file"
    override fun getDefaultExtension() = "sabaka"
    override fun getIcon(): Icon = SabakaIcons.FILE

    companion object {
        @JvmField val INSTANCE = SabakaFileType()
    }
}
