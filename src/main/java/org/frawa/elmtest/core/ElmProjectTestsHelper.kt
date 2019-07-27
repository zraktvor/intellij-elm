package org.frawa.elmtest.core

import com.intellij.openapi.project.Project
import org.elm.workspace.ElmProject
import org.elm.workspace.elmWorkspace
import java.nio.file.Files
import java.nio.file.Path

class ElmProjectTestsHelper(project: Project) {

    private val workspaceService = project.elmWorkspace

    private val testableProjects: List<ElmProject>
        get() = workspaceService.allProjects
                .filter { p -> Files.exists(p.projectDirPath.resolve("tests")) }

    fun allNames(): List<String> {
        return testableProjects.map { it.presentableName }
    }

    fun nameByProjectDirPath(path: String): String? {
        return testableProjects
                .filter { p -> p.projectDirPath.toString() == path }
                .map { it.presentableName }
                .firstOrNull()
    }

    fun projectDirPathByName(name: String): String? {
        return testableProjects
                .filter { it.presentableName == name }
                .map { it.projectDirPath.toString() }
                .firstOrNull()
    }

    fun elmProjectByProjectDirPath(path: String): ElmProject? {
        return testableProjects.firstOrNull { it.projectDirPath.toString() == path }
    }

    fun adjustElmCompilerProjectDirPath(elmFolder: String, compilerPath: Path): Path {
        val elmProject = elmProjectByProjectDirPath(elmFolder)
        // TODO [drop 0.18] no need to adjust the path for 19+
        return if (elmProject?.isElm18 == true)
            compilerPath.resolveSibling("elm-make")
        else
            compilerPath
    }

    companion object {
        fun elmFolderForTesting(elmProject: ElmProject): Path {
            // TODO [drop 0.18] simplify
            return if (elmProject.isElm18 && elmProject.presentableName == "tests")
                elmProject.projectDirPath.parent
            else
                elmProject.projectDirPath
        }
    }

}
