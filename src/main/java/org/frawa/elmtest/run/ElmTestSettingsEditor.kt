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
package org.frawa.elmtest.run;

import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import org.frawa.elmtest.core.ElmProjectTestsHelper;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ElmTestSettingsEditor extends SettingsEditor<ElmTestRunConfiguration> {
    private final ElmProjectTestsHelper helper;
    private JPanel myPanel;
    private ComboBox<String> projectChooser;

    ElmTestSettingsEditor(Project project) {
        helper = new ElmProjectTestsHelper(project);
        helper
                .allNames()
                .forEach(projectChooser::addItem);
    }

    @NotNull
    @Override
    protected JComponent createEditor() {
        return myPanel;
    }

    @Override
    protected void resetEditorFrom(@NotNull ElmTestRunConfiguration configuration) {
        String dirPath = configuration.getOptions().getElmFolder();
        if (dirPath == null) return;
        String name = helper.nameByProjectDirPath(dirPath);
        if (name != null) {
            projectChooser.setSelectedItem(name);
        }
    }

    @Override
    protected void applyEditorTo(@NotNull ElmTestRunConfiguration configuration) {
        String name = (String) projectChooser.getSelectedItem();
        if (name != null) {
            String folder = helper.projectDirPathByName(name);
            configuration.setOptions(new ElmTestRunConfiguration.Options(folder));
        }
    }
}
