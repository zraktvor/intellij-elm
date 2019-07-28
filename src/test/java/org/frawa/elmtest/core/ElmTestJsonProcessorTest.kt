package org.frawa.elmtest.core

import com.intellij.execution.testframework.sm.runner.events.TestFailedEvent
import com.intellij.execution.testframework.sm.runner.events.TestFinishedEvent
import com.intellij.execution.testframework.sm.runner.events.TestStartedEvent
import com.intellij.execution.testframework.sm.runner.events.TestSuiteStartedEvent
import org.intellij.lang.annotations.Language
import org.junit.Assert.*
import org.junit.Test

class ElmTestJsonProcessorTest {

    private val processor = ElmTestJsonProcessor()

    @Test
    fun runStart() {
        val list = processor.accept("""{"event":"runStart","testCount":"9","fuzzRuns":"100","paths":[],"initialSeed":"1448022641"}""")
        assertTrue(list!!.count() == 0)
    }

    @Test
    fun runComplete() {
        val list = processor
                .accept("""{"event":"runComplete","passed":"8","failed":"1","duration":"353","autoFail":null}""")
                ?.toList()
        assertTrue(list!!.isEmpty())
    }

    @Test
    fun testCompleted() {
        val list = processor
                .accept("""{"event":"testCompleted","status":"pass","labels":["Module","test"],"failures":[],"duration":"1"}""")
                ?.toList()
        assertEquals(3, list!!.size)
        assertTrue(list[0] is TestSuiteStartedEvent)
        assertTrue(list[1] is TestStartedEvent)
        assertTrue(list[2] is TestFinishedEvent)
        assertEquals("Module", list[0].name)
        assertEquals("test", list[1].name)
        assertEquals("test", list[2].name)
    }

    @Test
    fun testCompletedWithSlashes() {
        val list = processor
                .accept("""{"event":"testCompleted","status":"pass","labels":["Module","test / stuff"],"failures":[],"duration":"1"}""")
                ?.toList()
        assertEquals(3, list!!.size)
        assertTrue(list[0] is TestSuiteStartedEvent)
        assertTrue(list[1] is TestStartedEvent)
        assertTrue(list[2] is TestFinishedEvent)
        assertEquals("Module", list[0].name)
        assertEquals("test / stuff", list[1].name)
        assertEquals("test / stuff", list[2].name)
    }

//    @Test
//    fun statusIsTodo() {
//        @Language("JSON")
//        val text = """{"event":"testCompleted","status":"todo","labels":["Exploratory","describe"],"failures":["TODO comment"],"duration":"2"}"""
//        val obj = getObject(text)
//        assertEquals("TODO comment", getComment(obj))
//        assertNull(getMessage(obj))
//        assertNull(getExpected(obj))
//        assertNull(getActual(obj))
//    }
//
//    @Test
//    fun fail() {
//        @Language("JSON")
//        val text = """{"event":"testCompleted","status":"fail","labels":["Exploratory","describe","fail"],"failures":[{"given":null,"message":"boom","reason":{"type":"custom","data":"boom"}}],"duration":"1"}"""
//        val obj = getObject(text)
//        assertNull(getComment(obj))
//        assertEquals("boom", getMessage(obj))
//        assertNull(getExpected(obj))
//        assertNull(getActual(obj))
//    }
//
//    @Test
//    fun failEqual() {
//        @Language("JSON")
//        val text = """{"event":"testCompleted","status":"fail","labels":["Exploratory","describe","duplicate nested","ok1"],"failures":[{"given":null,"message":"Expect.equal","reason":{"type":"custom","data":{"expected":"\"value\"","actual":"\"value2\"","comparison":"Expect.equal"}}}],"duration":"2"}"""
//        val obj = getObject(text)
//        assertNull(getComment(obj))
//        assertEquals("Expect.equal", getMessage(obj))
//        assertEquals("\"value\"", getExpected(obj))
//        assertEquals("\"value2\"", getActual(obj))
//    }
//
//    @Test
//    fun failHtml() {
//        @Language("JSON")
//        val text = """{"event":"testCompleted","status":"fail","labels":["Exploratory","Html tests 1","... fails"],"failures":[{"given":null,"message":"▼ Query.fromHtml\n\n    <div class=\"container\">\n        <button>\n            I'm a button!\n        </button>\n    </div>\n\n\n▼ Query.find [ tag \"button1\" ]\n\n0 matches found for this query.\n\n\n✗ Query.find always expects to find 1 element, but it found 0 instead.","reason":{"type":"custom","data":"▼ Query.fromHtml\n\n    <div class=\"container\">\n        <button>\n            I'm a button!\n        </button>\n    </div>\n\n\n▼ Query.find [ tag \"button1\" ]\n\n0 matches found for this query.\n\n\n✗ Query.find always expects to find 1 element, but it found 0 instead."}}],"duration":"15"}"""
//        val obj = getObject(text)
//        assertNull(getComment(obj))
//
//        val message = getMessage(obj)
//        assertNotNull(message)
//        assertTrue(message!!.contains("I'm a button!"))
//
//        assertNull(getExpected(obj))
//        assertNull(getActual(obj))
//    }
//
//    @Test
//    fun failEqualLists() {
//        @Language("JSON")
//        val text = """{"event":"testCompleted","status":"fail","labels":["Deep.Exploratory","Variuous Fails","equalLists"],"failures":[{"given":null,"message":"Expect.equalLists","reason":{"type":"custom","data":{"expected":["\"one\"","\"expected\""],"actual":["\"one\"","\"actual\""]}}}],"duration":"1"}
//"""
//        val obj = getObject(text)
//        assertNull(getComment(obj))
//        assertNull(getComment(obj))
//        assertEquals("Expect.equalLists", getMessage(obj))
//        assertEquals("[\n" +
//                "  \"\\\"one\\\"\",\n" +
//                "  \"\\\"expected\\\"\"\n" +
//                "]", getExpected(obj))
//        assertEquals("[\n" +
//                "  \"\\\"one\\\"\",\n" +
//                "  \"\\\"actual\\\"\"\n" +
//                "]", getActual(obj))
//    }
//
//    @Test
//    fun failFallback() {
//        val text = """{"event":"testCompleted","status":"fail","labels":["Module","Fails"],"failures":[{"unknown": "format"}],"duration":"1"}"""
//        val obj = getObject(text)
//        val path = ElmTestJsonProcessor.toPath(obj)
//
//        val list = ElmTestJsonProcessor.testEvents(path, obj).toList()
//
//        assertEquals(2, list.size.toLong())
//        assertTrue(list[1] is TestFailedEvent)
//        assertEquals("[\n" +
//                "  {\n" +
//                "    \"unknown\": \"format\"\n" +
//                "  }\n" +
//                "]", (list[1] as TestFailedEvent).localizedFailureMessage)
//    }


    @Test
    fun testCompletedWithLocation() {
        val list = processor
                .accept("""{"event":"testCompleted","status":"pass","labels":["Module","test"],"failures":[],"duration":"1"}""")
                ?.toList()
        assertEquals(3, list!!.size)
        assertTrue(list[0] is TestSuiteStartedEvent)
        assertTrue(list[1] is TestStartedEvent)
        assertTrue(list[2] is TestFinishedEvent)
        assertEquals("elmTestDescribe://Module", (list[0] as TestSuiteStartedEvent).locationUrl)
        assertEquals("elmTestTest://Module/test", (list[1] as TestStartedEvent).locationUrl)
    }

    @Test
    fun testCompletedWithLocationNested() {
        val list = processor
                .accept("""{"event":"testCompleted","status":"pass","labels":["Nested.Module","test"],"failures":[],"duration":"1"}""")
                ?.toList()
        assertEquals(3, list!!.size)
        assertTrue(list[0] is TestSuiteStartedEvent)
        assertTrue(list[1] is TestStartedEvent)
        assertTrue(list[2] is TestFinishedEvent)
        assertEquals("elmTestDescribe://Nested.Module", (list[0] as TestSuiteStartedEvent).locationUrl)
        assertEquals("elmTestTest://Nested.Module/test", (list[1] as TestStartedEvent).locationUrl)
    }

    @Test
    fun testCompletedFailedWithLocation() {
        val list = processor
                .accept("""{"event":"testCompleted","status":"fail","labels":["Exploratory","describe","fail"],"failures":[{"given":null,"message":"boom","reason":{"type":"custom","data":"boom"}}],"duration":"1"}""")
                ?.toList()
        assertEquals(4, list!!.size)
        assertTrue(list[0] is TestSuiteStartedEvent)
        assertTrue(list[1] is TestSuiteStartedEvent)
        assertTrue(list[2] is TestStartedEvent)
        assertTrue(list[3] is TestFailedEvent)
        assertEquals("elmTestDescribe://Exploratory", (list[0] as TestSuiteStartedEvent).locationUrl)
        assertEquals("elmTestDescribe://Exploratory/describe", (list[1] as TestSuiteStartedEvent).locationUrl)
        assertEquals("elmTestTest://Exploratory/describe/fail", (list[2] as TestStartedEvent).locationUrl)
    }

    @Test
    fun acceptCompileErrors() {
        @Language("JSON")
        val json = """
            {
                "type": "compile-errors",
                "errors": [
                    {
                        "path": "PATH/tests/UiTests.elm",
                        "name": "UiTests",
                        "problems": [
                            {
                                "title": "TOO FEW ARGS",
                                "region": {
                                    "start": {
                                        "line": 131,
                                        "column": 33
                                    },
                                    "end": {
                                        "line": 131,
                                        "column": 39
                                    }
                                },
                                "message": [
                                    "The `Msg` type needs 1 argument, but I see 0 instead:\n\n131| update : Highlighter MyStyle -> IT.Msg -> Model MyStyle -> Model MyStyle\n                                     ",
                                    {
                                        "bold": false,
                                        "underline": false,
                                        "color": "red",
                                        "string": "^^^^^^"
                                    },
                                    "\nWhat is missing? Are some parentheses misplaced?"
                                ]
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()
        val list = processor.accept(json)?.toList()
        assertEquals(2, list!!.size)

        assertTrue(list[0] is TestStartedEvent)
        assertTrue(list[1] is TestFailedEvent)
        assertEquals("elmTestError://PATH/tests/UiTests.elm::131::33", (list[0] as TestStartedEvent).locationUrl)
        assertEquals("TOO FEW ARGS", list[0].name)
        assertEquals("TOO FEW ARGS", list[1].name)
    }

    @Test
    fun `accept compile errors with plaintext junk at the end`() {
        @Language("JSON")
        val json = """
            {
                "type": "compile-errors",
                "errors": [
                    {
                        "path": "PATH/tests/UiTests.elm",
                        "name": "UiTests",
                        "problems": [
                            {
                                "title": "TOO FEW ARGS",
                                "region": {
                                    "start": { "line": 131, "column": 33 },
                                    "end": { "line": 131, "column": 39 }
                                },
                                "message": [ "" ]
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()
        val list = processor.accept(json + "Compilation failed blah blah blah")?.toList()
        assertEquals(2, list!!.size)

        assertTrue(list[0] is TestStartedEvent)
        assertTrue(list[1] is TestFailedEvent)
        assertEquals("elmTestError://PATH/tests/UiTests.elm::131::33", (list[0] as TestStartedEvent).locationUrl)
        assertEquals("TOO FEW ARGS", list[0].name)
        assertEquals("TOO FEW ARGS", list[1].name)
    }

    @Test
    fun `plaintext junk without any JSON in front of it`() {
        val list = processor.accept("Compilation failed BLA")
        assertNull(list)
    }

    @Test
    fun acceptEmptyText() {
        val list = processor.accept("")
        assertNull(list)
    }

    @Test
    fun junk() {
        val list = processor.accept("junk")
        assertNull(list)
    }
}