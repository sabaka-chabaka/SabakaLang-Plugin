package com.sabakachabaka.sabakalang

import com.intellij.lang.Language

object SabakaLanguage : Language("SabakaLang") {
    private fun readResolve(): Any = SabakaLanguage
    override fun getDisplayName() = "SabakaLang"
}
