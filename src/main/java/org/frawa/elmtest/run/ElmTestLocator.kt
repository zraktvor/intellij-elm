package org.frawa.elmtest.run

import com.intellij.execution.Location
import com.intellij.execution.PsiLocation
import com.intellij.execution.testframework.sm.FileUrlProvider
import com.intellij.execution.testframework.sm.TestsLocationProviderUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import org.elm.lang.core.psi.ElmAtomTag
import org.elm.lang.core.psi.ElmOperandTag
import org.elm.lang.core.psi.ElmTypes
import org.elm.lang.core.psi.elements.ElmFunctionCallExpr
import org.elm.lang.core.psi.elements.ElmStringConstantExpr
import org.elm.openapiext.toPsiFile
import org.frawa.elmtest.core.LabelUtils
import java.nio.file.Path
import java.nio.file.Paths


object ElmTestLocator : FileUrlProvider() {

    override fun getLocation(protocol: String, path: String, metainfo: String?, project: Project, scope: GlobalSearchScope): List<Location<*>> {
        if (!protocol.startsWith(LabelUtils.ELM_TEST_PROTOCOL)) {
            return super.getLocation(protocol, path, metainfo, project, scope)
        }

        if (protocol.startsWith(LabelUtils.ERROR_PROTOCOL)) {
            val pair = LabelUtils.fromErrorLocationUrlPath(path)
            val filePath = pair.first
            val line = pair.second.first
            val column = pair.second.second
            val systemIndependentPath = FileUtil.toSystemIndependentName(filePath)
            return TestsLocationProviderUtil.findSuitableFilesFor(systemIndependentPath, project)
                    .mapNotNull { getErrorLocation(line, column, project, it) }
        }

        val pair = LabelUtils.fromLocationUrlPath(path)
        val filePath = pair.first
        val labels = pair.second
        val systemIndependentPath = FileUtil.toSystemIndependentName(filePath)
        val isDescribe = LabelUtils.DESCRIBE_PROTOCOL == protocol

        return TestsLocationProviderUtil.findSuitableFilesFor(systemIndependentPath, project)
                .mapNotNull { getLocation(isDescribe, labels, project, it) }
    }

    private fun getLocation(isDescribe: Boolean, labels: String, project: Project, virtualFile: VirtualFile): Location<*>? {
        val psiFile = virtualFile.toPsiFile(project) ?: return null
        val found = ElmPluginHelper.getPsiElement(isDescribe, labels, psiFile)
        return PsiLocation.fromPsiElement(project, found)
    }

    private fun getErrorLocation(line: Int, column: Int, project: Project, virtualFile: VirtualFile): Location<*>? {
        val psiFile = virtualFile.toPsiFile(project) ?: return null
        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
                ?: return PsiLocation.fromPsiElement(project, psiFile)

        val offset = document.getLineStartOffset(line - 1) + column - 1
        val element = psiFile.findElementAt(offset)
                ?: return PsiLocation.fromPsiElement(project, psiFile)

        return PsiLocation.fromPsiElement(project, element)
    }
}


object ElmPluginHelper {

    fun getPsiElement(isDescribe: Boolean, labels: String, file: PsiFile): PsiElement {
        return getPsiElement(isDescribe, Paths.get(labels), file)
    }

    private fun getPsiElement(isDescribe: Boolean, labelPath: Path, file: PsiFile): PsiElement {
        return findPsiElement(isDescribe, labelPath, file)
                ?: labelPath.parent?.let { getPsiElement(isDescribe, it, file) }
                ?: file
    }

    private fun findPsiElement(isDescribe: Boolean, labelPath: Path, file: PsiFile): PsiElement? {
        val labels = labels(labelPath)

        if (labels.isEmpty()) {
            return null
        }

        val topLabel = LabelUtils.decodeLabel(Paths.get(labels.first()))
        val subLabels = labels.drop(1)

        if (subLabels.isEmpty() && !isDescribe) {
            return allTests(topLabel)(file).firstOrNull(topLevel())
        }

        val topSuites = allSuites(topLabel)(file)
                .filter(topLevel())

        if (isDescribe) {
            return subSuites(subLabels, topSuites).firstOrNull()
        }

        val deepestSuites = subSuites(subLabels.dropLast(1), topSuites)
        val leafLabel = LabelUtils.decodeLabel(Paths.get(labels.last()))
        return deepestSuites.map(secondOperand())
                .flatMap(allTests(leafLabel))
                .firstOrNull()
    }

    private fun subSuites(labels: List<String>, tops: List<ElmFunctionCallExpr>): List<ElmFunctionCallExpr> {
        return labels
                .fold(tops)
                { acc, label ->
                    acc
                            .map(secondOperand())
                            .flatMap(allSuites(label))
                }
    }

    private fun labels(path: Path): List<String> {
        return (0 until path.nameCount)
                .map { path.getName(it) }
                .map { it.toString() }
                .toList()
    }

    private fun allSuites(label: String): (PsiElement) -> List<ElmFunctionCallExpr> {
        return {
            functionCalls(it, "describe")
                    .filter(firstArgumentIsString(label))
        }
    }

    private fun allTests(label: String): (PsiElement) -> List<ElmFunctionCallExpr> {
        return {
            functionCalls(it, "test")
                    .filter(firstArgumentIsString(label))
        }
    }

    private fun functionCalls(parent: PsiElement, targetName: String): List<ElmFunctionCallExpr> {
        return PsiTreeUtil.findChildrenOfType(parent, ElmFunctionCallExpr::class.java)
                .filter { it.target.text == targetName }
    }

    private fun topLevel(): (ElmFunctionCallExpr) -> Boolean {
        return { null == PsiTreeUtil.findFirstParent(it, true) { element -> isSuite(element) } }
    }

    private fun isSuite(element: PsiElement): Boolean {
        return element is ElmFunctionCallExpr && element.target.text == "describe"
    }

    private fun firstArgumentIsString(value: String): (ElmFunctionCallExpr) -> Boolean {
        return { literalString()(firstOperand()(it)) == value }
    }

    private fun firstOperand(): (ElmFunctionCallExpr) -> ElmOperandTag {
        return { it.arguments.first() }
    }

    private fun secondOperand(): (ElmFunctionCallExpr) -> ElmAtomTag {
        return { it.arguments.drop(1).first() }
    }

    private fun literalString(): (ElmOperandTag) -> String {
        return { stringConstant(it) }
    }

    private fun stringConstant(op: ElmOperandTag): String {
        return if (op is ElmStringConstantExpr) {
            PsiTreeUtil.findSiblingForward(op.getFirstChild(), ElmTypes.REGULAR_STRING_PART, null)!!.text
        } else {
            PsiTreeUtil.findChildOfType(op, ElmStringConstantExpr::class.java)!!.text
        }
    }
}
