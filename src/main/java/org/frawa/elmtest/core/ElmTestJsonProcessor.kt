package org.frawa.elmtest.core

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import com.intellij.execution.testframework.sm.runner.events.*
import org.elm.workspace.compiler.ElmError
import org.elm.workspace.compiler.elmJsonToCompilerMessages
import org.frawa.elmtest.core.LabelUtils.commonParent
import org.frawa.elmtest.core.LabelUtils.getName
import org.frawa.elmtest.core.LabelUtils.subParents
import org.frawa.elmtest.core.LabelUtils.toSuiteLocationUrl
import org.frawa.elmtest.core.LabelUtils.toTestLocationUrl
import org.intellij.lang.annotations.Language
import java.nio.file.Path

class ElmTestJsonProcessor {

    private var currentPath = LabelUtils.EMPTY_PATH

    fun accept(@Language("JSON") text: String): Sequence<TreeNodeEvent>? {
        try {
            val obj: JsonObject = gson.fromJson(text, JsonObject::class.java) ?: return null
            if ("compile-errors" == obj.get("type")?.asString) {
                return accept(elmJsonToCompilerMessages(text))
            }

            val event = obj.get("event")?.asString
            if ("runStart" == event) {
                currentPath = LabelUtils.EMPTY_PATH
                return emptySequence()
            } else if ("runComplete" == event) {
                val closeAll = closeSuitePaths(currentPath, LabelUtils.EMPTY_PATH)
                        .map { newTestSuiteFinishedEvent(it) }
                currentPath = LabelUtils.EMPTY_PATH
                return closeAll
            } else if ("testCompleted" != event) {
                return emptySequence()
            }

            var path = toPath(obj)
            if ("todo" == getStatus(obj)) {
                path = path.resolve("todo")
            }

            val result: Sequence<TreeNodeEvent> = closeSuitePaths(currentPath, path)
                    .map { this.newTestSuiteFinishedEvent(it) }
                    .plus(openSuitePaths(currentPath, path).map { this.newTestSuiteStartedEvent(it) })
                    .plus(testEvents(path, obj))

            currentPath = path
            return result

        } catch (e: JsonSyntaxException) {
            if (text.contains("Compilation failed")) {
                val json = text.substring(0, text.lastIndexOf("Compilation failed"))
                val obj = gson.fromJson(json, JsonObject::class.java) ?: return null
                if ("compile-errors" == obj.get("type")?.asString) {
                    return accept(elmJsonToCompilerMessages(json))
                }
            }
            return null
        }

    }

    private fun newTestSuiteStartedEvent(path: Path): TestSuiteStartedEvent {
        return TestSuiteStartedEvent(getName(path), toSuiteLocationUrl(path))
    }

    private fun newTestSuiteFinishedEvent(path: Path): TestSuiteFinishedEvent {
        return TestSuiteFinishedEvent(getName(path))
    }

    private fun accept(compileErrors: List<ElmError>): Sequence<TreeNodeEvent> {
        return compileErrors.asSequence().flatMap {
            sequenceOf(
                    TestStartedEvent(it.title, it.location?.toTestErrorLocationUrl()),
                    TestFailedEvent(it.title, null, it.plaintext, null, true, null, null, null, null, false, false, -1)
            )
        }
    }


    companion object {

        private val gson = GsonBuilder().setPrettyPrinting().create()

        fun testEvents(path: Path, obj: JsonObject): Sequence<TreeNodeEvent> {
            val status = getStatus(obj)
            if ("pass" == status) {
                val duration = java.lang.Long.parseLong(obj.get("duration").asString)
                return sequenceOf(newTestStartedEvent(path))
                        .plus(newTestFinishedEvent(path, duration))
            } else if ("todo" == status) {
                val comment = getComment(obj)
                return sequenceOf(newTestIgnoredEvent(path, comment))
            }
            try {
                val message = getMessage(obj)
                val actual = getActual(obj)
                val expected = getExpected(obj)

                return sequenceOf(newTestStartedEvent(path))
                        .plus(newTestFailedEvent(path, actual, expected, message
                                ?: ""))
            } catch (e: Throwable) {
                val failures = GsonBuilder().setPrettyPrinting().create().toJson(obj.get("failures"))
                return sequenceOf(newTestStartedEvent(path))
                        .plus(newTestFailedEvent(path, null, null, failures))
            }

        }

        private fun newTestIgnoredEvent(path: Path, comment: String?): TestIgnoredEvent {
            return TestIgnoredEvent(getName(path), sureText(comment), null)
        }

        private fun newTestFinishedEvent(path: Path, duration: Long): TestFinishedEvent {
            return TestFinishedEvent(getName(path), duration)
        }

        private fun newTestFailedEvent(path: Path, actual: String?, expected: String?, message: String): TestFailedEvent {
            return TestFailedEvent(getName(path), sureText(message), null, false, actual, expected)
        }

        private fun newTestStartedEvent(path: Path): TestStartedEvent {
            return TestStartedEvent(getName(path), toTestLocationUrl(path))
        }

        private fun sureText(comment: String?): String {
            return comment ?: ""
        }

        fun getComment(obj: JsonObject): String? {
            return if (getFirstFailure(obj).isJsonPrimitive)
                getFirstFailure(obj).asString
            else
                null
        }

        fun getMessage(obj: JsonObject): String? {
            return if (getFirstFailure(obj).isJsonObject)
                getFirstFailure(obj).asJsonObject.get("message").asString
            else
                null
        }

        fun getReason(obj: JsonObject): JsonObject? {
            return if (getFirstFailure(obj).isJsonObject)
                getFirstFailure(obj).asJsonObject.get("reason").asJsonObject
            else
                null
        }

        private fun getData(obj: JsonObject): JsonObject? {
            val reason = getReason(obj)
            return if (reason != null)
                if (reason.get("data") != null)
                    if (reason.get("data").isJsonObject)
                        reason.get("data").asJsonObject
                    else
                        null
                else
                    null
            else
                null
        }

        fun getActual(obj: JsonObject): String? {
            return pretty(getDataMember(obj, "actual"))
        }

        fun getExpected(obj: JsonObject): String? {
            return pretty(getDataMember(obj, "expected"))
        }

        private fun getDataMember(obj: JsonObject, name: String): JsonElement? {
            val data = getData(obj)
            return data?.get(name)
        }

        private fun pretty(element: JsonElement?): String? {
            return if (element != null)
                if (element.isJsonPrimitive)
                    element.asString
                else
                    gson.toJson(element)
            else
                null
        }

        private fun getFirstFailure(obj: JsonObject): JsonElement {
            return obj.get("failures").asJsonArray.get(0)
        }

        private fun getStatus(obj: JsonObject): String {
            return obj.get("status").asString
        }

        fun toPath(element: JsonObject): Path {
            return LabelUtils.toPath(
                    element.get("labels").asJsonArray.iterator().asSequence()
                            .map { it.asString }
                            .toList()
            )
        }

        fun closeSuitePaths(from: Path, to: Path): Sequence<Path> {
            val commonParent = commonParent(from, to)
            return subParents(from, commonParent).asSequence()
        }

        fun openSuitePaths(from: Path, to: Path): Sequence<Path> {
            val commonParent = commonParent(from, to)
            return subParents(to, commonParent).toList().reversed().asSequence()
        }
    }

}