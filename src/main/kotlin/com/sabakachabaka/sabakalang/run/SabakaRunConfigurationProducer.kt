package com.sabakachabaka.sabakalang.run

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.sabakachabaka.sabakalang.psi.SabakaFile

class SabakaRunConfigurationProducer :
    LazyRunConfigurationProducer<SabakaRunConfiguration>() {

    override fun getConfigurationFactory(): ConfigurationFactory =
        ConfigurationTypeUtil.findConfigurationType(SabakaRunConfigurationType::class.java)
            .configurationFactories[0]

    override fun setupConfigurationFromContext(
        cfg: SabakaRunConfiguration,
        ctx: ConfigurationContext,
        source: Ref<PsiElement>
    ): Boolean {
        val file = ctx.psiLocation?.containingFile as? SabakaFile ?: return false
        val vFile = file.virtualFile ?: return false
        cfg.scriptPath       = vFile.path
        cfg.name             = vFile.nameWithoutExtension
        cfg.workingDirectory = vFile.parent?.path ?: ""
        return true
    }

    override fun isConfigurationFromContext(
        cfg: SabakaRunConfiguration,
        ctx: ConfigurationContext
    ): Boolean {
        val file = ctx.psiLocation?.containingFile as? SabakaFile ?: return false
        return cfg.scriptPath == (file.virtualFile?.path ?: "")
    }
}
