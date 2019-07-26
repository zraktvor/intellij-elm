package org.frawa.elmtest.run

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import org.elm.lang.core.psi.ElmFile
import org.frawa.elmtest.core.ElmProjectTestsHelper

class ElmTestRunConfigurationProducer : LazyRunConfigurationProducer<ElmTestRunConfiguration>() {

    override fun getConfigurationFactory() =
            ElmTestConfigurationFactory(ElmTestRunConfigurationType())

    override fun setupConfigurationFromContext(configuration: ElmTestRunConfiguration, context: ConfigurationContext, sourceElement: Ref<PsiElement>): Boolean {
        val elmFolder = getCandidateElmFolder(context) ?: return false
        configuration.options = ElmTestRunConfiguration.Options(elmFolder)
        configuration.setGeneratedName()
        return true
    }

    override fun isConfigurationFromContext(configuration: ElmTestRunConfiguration, context: ConfigurationContext): Boolean {
        val elmFolder = getCandidateElmFolder(context)
        return elmFolder != null && elmFolder == configuration.options.elmFolder
    }

    private fun getCandidateElmFolder(context: ConfigurationContext): String? {
        val elmFile = context.psiLocation?.containingFile as? ElmFile ?: return null
        val elmProject = elmFile.elmProject ?: return null
        return ElmProjectTestsHelper.elmFolderForTesting(elmProject).toString()
    }
}
