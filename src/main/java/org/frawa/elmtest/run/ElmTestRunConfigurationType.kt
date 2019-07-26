package org.frawa.elmtest.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType

class ElmTestRunConfigurationType : ConfigurationType {

    override fun getDisplayName() = "Elm Test"
    override fun getConfigurationTypeDescription() = "Elm Test Runner"
    override fun getId() = "ELM_TEST_RUN_CONFIGURATION"
    override fun getIcon() = ElmTestConfigurationFactory.RUN_ICON

    override fun getConfigurationFactories(): Array<ConfigurationFactory> =
            arrayOf(ElmTestConfigurationFactory(this))
}

