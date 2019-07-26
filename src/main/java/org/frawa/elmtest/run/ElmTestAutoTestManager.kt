package org.frawa.elmtest.run

import com.intellij.execution.testframework.autotest.AbstractAutoTestManager
import com.intellij.execution.testframework.autotest.AutoTestWatcher
import com.intellij.execution.testframework.autotest.DelayedDocumentWatcher
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Condition
import com.intellij.util.Consumer
import org.elm.lang.core.ElmFileType

@State(
        name = "ElmTestAutoTestManager",
        storages = [Storage(StoragePathMacros.WORKSPACE_FILE)]
)
class ElmTestAutoTestManager(project: Project) : AbstractAutoTestManager(project) {

    override fun createWatcher(project: Project): AutoTestWatcher =
            DelayedDocumentWatcher(project,
                    myDelayMillis,
                    Consumer<Int> { restartAllAutoTests(it) },
                    Condition { file -> FileEditorManager.getInstance(project).isFileOpen(file) && ElmFileType == file.fileType }
            )

    companion object {
        fun getInstance(project: Project): ElmTestAutoTestManager = project.service()
    }
}
