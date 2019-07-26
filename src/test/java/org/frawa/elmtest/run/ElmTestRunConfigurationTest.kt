package org.frawa.elmtest.run

import org.jdom.Element
import org.junit.Assert.assertEquals
import org.junit.Test

class ElmTestRunConfigurationTest {

    @Test
    fun writeOptions() {
        val root = Element("ROOT")

        val options = ElmTestRunConfiguration.Options("folder")
        options.toXml(root)

        assertEquals(1, root.children.size.toLong())
        assertEquals(ElmTestRunConfiguration::class.java.simpleName, root.children[0].name)
        assertEquals(1, root.children[0].attributes.size.toLong())
        assertEquals("elm-folder", root.children[0].attributes[0].name)
        assertEquals("folder", root.children[0].attributes[0].value)
    }

    @Test
    fun roundTrip() {
        val root = Element("ROOT")

        val options = ElmTestRunConfiguration.Options("folder")
        options.toXml(root)
        val options2 = ElmTestRunConfiguration.Options.fromXml(root)

        assertEquals(options.elmFolder, options2.elmFolder)
    }

}