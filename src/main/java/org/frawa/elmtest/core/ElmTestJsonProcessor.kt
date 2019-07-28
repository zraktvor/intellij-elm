package org.frawa.elmtest.core

import com.google.gson.*
import com.google.gson.annotations.JsonAdapter
import com.intellij.execution.testframework.sm.runner.events.*
import org.elm.workspace.compiler.elmJsonToCompilerMessages
import org.intellij.lang.annotations.Language
import java.lang.reflect.Type

private val gson = Gson()

// JSON DTOs emitted by `elm-test --report json`

@JsonAdapter(ElmTestEvent.Deserializer::class)
sealed class ElmTestEvent {
    /**
     * The beginning of all tests suites in the Elm project
     */
    data class RunStart(
            val testCount: String,
            val fuzzRuns: String,
            val initialSeed: String,
            val paths: List<String>
    ) : ElmTestEvent()

    /**
     * The completion of all tests suites in the Elm project
     */
    data class RunComplete(
            val passed: String,
            val failed: String,
            val duration: String,
            val paths: List<String>,
            val autoFail: String? // TODO [kl] figure out what this is: only thing I've seen is "Test.skip was used"
    ) : ElmTestEvent()

    /**
     * The completion of an individual test (e.g. `Expect.equal (2+2) 4`)
     */
    data class TestCompleted(
            val status: String,
            val labels: List<String>,
            val failures: List<Failure>,
            val duration: String
    ) : ElmTestEvent()

    class Deserializer : JsonDeserializer<ElmTestEvent> {
        override fun deserialize(element: JsonElement, typeOf: Type, context: JsonDeserializationContext): ElmTestEvent {
            if (!element.isJsonObject) throw JsonParseException("Expected an elm-test event object")
            val obj = element.asJsonObject
            return when (val eventType = obj["event"].asString) {
                "runStart" -> gson.fromJson(obj, ElmTestEvent.RunStart::class.java)
                "runComplete" -> gson.fromJson(obj, ElmTestEvent.RunComplete::class.java)
                "testCompleted" -> gson.fromJson(obj, ElmTestEvent.TestCompleted::class.java)
                else -> throw JsonParseException("Unknown event type '$eventType'")
            }
        }
    }

    @JsonAdapter(Failure.Deserializer::class)
    sealed class Failure {
        data class Simple(val message: String) : Failure()
        data class Detailed(
                val given: Any?, // TODO [kl] figure out what this is
                val message: String,
                val reason: FailureReason
        ) : Failure()

        class Deserializer : JsonDeserializer<Failure> {
            override fun deserialize(element: JsonElement, typeOf: Type, context: JsonDeserializationContext): Failure {
                return if (element.isJsonPrimitive)
                    Simple(element.asString)
                else
                    gson.fromJson(element, Detailed::class.java)
            }
        }
    }


    data class FailureReason(
            val type: String,
            val data: FailureReasonData
    )

    data class FailureReasonData(
            val expected: String,
            val actual: String,
            val comparison: String
    )
}


// END JSON DTOs


class ElmTestJsonProcessor {

    fun accept(@Language("JSON") text: String): Sequence<TreeNodeEvent>? {
        val obj: JsonObject = try {
            gson.fromJson(text, JsonObject::class.java)
        } catch (e: JsonParseException) {
            if (text.contains("Compilation failed")) {
                // elm-test stdout is sloppy: sometimes it emits JSON followed by some plaintext.
                val fixedText = text.substring(0, text.indexOf("Compilation failed"))
                gson.fromJson(fixedText, JsonObject::class.java) ?: return null
            } else {
                return null
            }
        }

        // elm-test can emit 2 completely different kinds of responses to stdout:
        //
        // (1) compiler errors as if you ran `elm make --report=json`
        // (2) elm-test events (e.g. "runStart", "testCompleted")

        if ("compile-errors" == obj.get("type")?.asString) {
            return elmJsonToCompilerMessages(obj).asSequence().flatMap {
                sequenceOf(
                        TestStartedEvent(it.title, it.location?.toTestErrorLocationUrl()),
                        TestFailedEvent(it.title, it.plaintext, null, true, null, null)
                )
            }
        }

        // TODO generate test suite start/end events
        return when (val event = gson.fromJson(obj, ElmTestEvent::class.java)) {
            is ElmTestEvent.RunStart -> emptySequence()
            is ElmTestEvent.RunComplete -> emptySequence()
            is ElmTestEvent.TestCompleted -> {
                return sequenceOf(
                        TestStartedEvent(event.testName, null),
                        when (event.status) {
                            "pass" -> event.passed()
                            "todo" -> event.ignored()
                            "fail" -> event.failed()
                            else -> error("unexpected status: '${event.status}'")
                        }
                )
            }
        }
    }
}

private val ElmTestEvent.TestCompleted.testName: String
    get() = labels.joinToString("/")

private fun ElmTestEvent.TestCompleted.passed(): TreeNodeEvent {
    check(status == "pass")
    return TestFinishedEvent(testName, duration.toLong())
}

private fun ElmTestEvent.TestCompleted.ignored(): TreeNodeEvent {
    check(status == "todo")
    val comment = (failures.first() as ElmTestEvent.Failure.Simple).message
    return TestIgnoredEvent(testName, null, comment, null)
}

private fun ElmTestEvent.TestCompleted.failed(): TreeNodeEvent {
    check(status == "fail")
    val (msg, actual, expected) = run {
        when (val failure = failures.first()) {
            is ElmTestEvent.Failure.Simple -> Triple(failure.message, null, null)
            is ElmTestEvent.Failure.Detailed ->
                Triple(
                        failure.message,
                        failure.reason.data.actual,
                        failure.reason.data.expected
                )
        }
    }

    return TestFailedEvent(
            testName,
            msg,
            null,  // stackTrace
            false, // testError
            actual,
            expected)
}