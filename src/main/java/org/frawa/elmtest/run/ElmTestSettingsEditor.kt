// Copyright 2000-2017 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package org.frawa.elmtest.run

import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import org.frawa.elmtest.core.ElmProjectTestsHelper
import javax.swing.JComponent
import javax.swing.JPanel

class ElmTestSettingsEditor(project: Project) : SettingsEditor<ElmTestRunConfiguration>() {
    private val helper: ElmProjectTestsHelper = ElmProjectTestsHelper(project)
    private lateinit var myPanel: JPanel
    private lateinit var projectChooser: ComboBox<String>

    init {
        helper.allNames().forEach { projectChooser.addItem(it) }
    }

    override fun createEditor(): JComponent {
        return myPanel
    }

    override fun resetEditorFrom(configuration: ElmTestRunConfiguration) {
        val dirPath = configuration.options.elmFolder ?: return
        val name = helper.nameByProjectDirPath(dirPath)
        if (name != null) {
            projectChooser.selectedItem = name
        }
    }

    override fun applyEditorTo(configuration: ElmTestRunConfiguration) {
        val name = projectChooser.selectedItem as String
        val folder = helper.projectDirPathByName(name)
        configuration.options = ElmTestRunConfiguration.Options(folder)
    }
}
