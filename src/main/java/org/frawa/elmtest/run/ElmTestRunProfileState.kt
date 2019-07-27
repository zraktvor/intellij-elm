package org.frawa.elmtest.run

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.execution.testframework.autotest.ToggleAutoTestAction
import com.intellij.execution.testframework.sm.SMCustomMessagesParsing
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil
import com.intellij.execution.testframework.sm.runner.OutputToGeneralTestEventsConverter
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties
import com.intellij.execution.testframework.sm.runner.SMTestLocator
import com.intellij.execution.testframework.sm.runner.events.*
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView
import com.intellij.execution.ui.ConsoleView
import com.intellij.notification.NotificationType
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageVisitor
import org.elm.ide.notifications.showBalloon
import org.elm.workspace.ElmWorkspaceService
import org.elm.workspace.elmWorkspace
import org.frawa.elmtest.core.ElmProjectTestsHelper
import org.frawa.elmtest.core.ElmTestJsonProcessor
import java.nio.file.Files

class ElmTestRunProfileState(
        environment: ExecutionEnvironment,
        private val configuration: ElmTestRunConfiguration
) : CommandLineState(environment) {

    private fun getElmFolder(): String? =
            configuration.options.elmFolder
                    ?.takeIf { it.isNotBlank() }
                    ?: environment.project.basePath

    @Throws(ExecutionException::class)
    override fun startProcess(): ProcessHandler {
        FileDocumentManager.getInstance().saveAllDocuments()
        val project = environment.project
        val workspace = project.elmWorkspace
        val toolchain = workspace.settings.toolchain
        val elmTestCLI = toolchain.elmTestCLI ?: handleBadConfig(workspace, "Could not find elm-test")
        val elmCompilerBinary = toolchain.elmCompilerPath
                ?: handleBadConfig(workspace, "Could not find the Elm compiler")

        val useFolder = getElmFolder()
        val adjusted = ElmProjectTestsHelper(project).adjustElmCompilerProjectDirPath(useFolder, elmCompilerBinary)

        return if (!Files.exists(adjusted)) {
            handleBadConfig(workspace, "Could not find the Elm compiler (elm-make)")
        } else {
            elmTestCLI.runTestsProcessHandler(adjusted, useFolder!!)
        }
    }

    @Throws(ExecutionException::class)
    override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult {
        val result = super.execute(executor, runner)
        if (result is DefaultExecutionResult) {
            result.setRestartActions(object : ToggleAutoTestAction() {
                override fun getAutoTestManager(project: Project) = ElmTestAutoTestManager.getInstance(project)
            })
        }
        return result
    }

    private fun handleBadConfig(workspaceService: ElmWorkspaceService, errorMessage: String): Nothing {
        workspaceService.intellijProject.showBalloon(errorMessage, NotificationType.ERROR,
                "Fix" to { workspaceService.showConfigureToolchainUI() }
        )
        throw ExecutionException(errorMessage)
    }

    override fun createConsole(executor: Executor): ConsoleView? {
        val runConfiguration = this.environment.runProfile as RunConfiguration
        val properties = ConsoleProperties(runConfiguration, executor)
        val consoleView = SMTRunnerConsoleView(properties)
        SMTestRunnerConnectionUtil.initConsoleView(consoleView, properties.testFrameworkName)
        return consoleView
    }


    private class ConsoleProperties(
            config: RunConfiguration,
            executor: Executor
    ) : SMTRunnerConsoleProperties(config, "elm-test", executor), SMCustomMessagesParsing {

        init {
            setIfUndefined(TestConsoleProperties.TRACK_RUNNING_TEST, true)
            setIfUndefined(TestConsoleProperties.OPEN_FAILURE_LINE, true)
            setIfUndefined(TestConsoleProperties.HIDE_PASSED_TESTS, false)
            setIfUndefined(TestConsoleProperties.SHOW_STATISTICS, true)
            setIfUndefined(TestConsoleProperties.SELECT_FIRST_DEFECT, true)
            setIfUndefined(TestConsoleProperties.SCROLL_TO_SOURCE, true)
        }

        override fun createTestEventsConverter(testFrameworkName: String, consoleProperties: TestConsoleProperties) =
                object : OutputToGeneralTestEventsConverter(testFrameworkName, consoleProperties) {
                    var processor = ElmTestJsonProcessor()

                    override fun processServiceMessages(text: String, outputType: Key<*>?, visitor: ServiceMessageVisitor): Boolean {
                        val events = processor.accept(text) ?: return false
                        events.forEach { processEvent(it) }
                        return true
                    }

                    private fun processEvent(event: TreeNodeEvent) {
                        when (event) {
                            is TestStartedEvent -> getProcessor().onTestStarted(event)
                            is TestFinishedEvent -> getProcessor().onTestFinished(event)
                            is TestFailedEvent -> getProcessor().onTestFailure(event)
                            is TestIgnoredEvent -> getProcessor().onTestIgnored(event)
                            is TestSuiteStartedEvent -> getProcessor().onSuiteStarted(event)
                            is TestSuiteFinishedEvent -> getProcessor().onSuiteFinished(event)
                        }
                    }
                }

        override fun getTestLocator(): SMTestLocator? = ElmTestLocator
    }
}
