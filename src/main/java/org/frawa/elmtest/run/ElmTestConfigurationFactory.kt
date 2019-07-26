package org.frawa.elmtest.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project
import org.elm.ide.icons.ElmIcons

class ElmTestConfigurationFactory(type: ConfigurationType) : ConfigurationFactory(type) {

    override fun createTemplateConfiguration(project: Project): RunConfiguration =
            ElmTestRunConfiguration(project, this, "Elm Test")

    override fun getIcon() = RUN_ICON
    override fun getName() = "Elm Test configuration factory"

    companion object {
        val RUN_ICON = ElmIcons.COLORFUL
    }
}