package org.frawa.elmtest.run

import com.google.common.annotations.VisibleForTesting
import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.LocatableConfigurationBase
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.InvalidDataException
import com.intellij.openapi.util.WriteExternalException
import org.jdom.Element
import java.nio.file.Paths

class ElmTestRunConfiguration(
        project: Project,
        factory: ConfigurationFactory,
        name: String
) : LocatableConfigurationBase<ElmTestRunProfileState>(project, factory, name) {

    var options = Options(elmFolder = null)

    data class Options(val elmFolder: String?) {
        // The serialized XML looks like this:
        // <ElmTestRunConfiguration elm-folder=""/>

        @VisibleForTesting
        internal fun toXml(element: Element) {
            val e = Element(ROOT_KEY)
            if (elmFolder != null) {
                e.setAttribute(FOLDER_KEY, elmFolder)
            }
            element.addContent(e)
        }

        companion object {
            const val ROOT_KEY = "ElmTestRunConfiguration"
            const val FOLDER_KEY = "elm-folder"

            @VisibleForTesting
            internal fun fromXml(element: Element): Options {
                val folder = element.getChild(ROOT_KEY)
                        ?.getAttribute(FOLDER_KEY)
                        ?.value
                return Options(folder)
            }
        }
    }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> =
            ElmTestSettingsEditor(project)

    override fun checkConfiguration() {}

    override fun getState(executor: Executor, executionEnvironment: ExecutionEnvironment) =
            ElmTestRunProfileState(executionEnvironment, this)

    override fun getIcon() = ElmTestConfigurationFactory.RUN_ICON

    @Throws(InvalidDataException::class)
    override fun readExternal(element: Element) {
        options = Options.fromXml(element)
    }

    @Throws(WriteExternalException::class)
    override fun writeExternal(element: Element) {
        options.toXml(element)
    }

    override fun suggestedName(): String? =
            options.elmFolder
                    ?.let { Paths.get(it).fileName }
                    ?.let { "Tests in $it" }
}
