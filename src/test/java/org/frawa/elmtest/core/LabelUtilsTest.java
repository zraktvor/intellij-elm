package org.frawa.elmtest.core;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.junit.Test;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.frawa.elmtest.core.LabelUtils.INSTANCE;
import static org.junit.Assert.assertEquals;

public class LabelUtilsTest {

    @Test
    public void locationUrl() {
        String url = INSTANCE.toTestLocationUrl(INSTANCE.toPath(Arrays.asList("Module", "test")));
        assertEquals("elmTestTest://Module/test", url);
    }

    @Test
    public void suiteLocationUrl() {
        String url = INSTANCE.toSuiteLocationUrl(INSTANCE.toPath(Arrays.asList("Module", "suite")));
        assertEquals("elmTestDescribe://Module/suite", url);
    }

    @Test
    public void locationUrlWithSlash() {
        String url = INSTANCE.toTestLocationUrl(INSTANCE.toPath(Arrays.asList("Nested.Module", "test / stuff")));
        assertEquals("elmTestTest://Nested.Module/test+%2F+stuff", url);
    }

    @Test
    public void useLocationUrl() {
        String url = INSTANCE.toTestLocationUrl(INSTANCE.toPath(Arrays.asList("Nested.Module", "test")));
        String urlPath = url.substring(url.indexOf("://") + 3);

        Pair<String, String> pair = INSTANCE.fromLocationUrlPath(urlPath);
        assertEquals("tests/Nested/Module.elm", pair.first);
        assertEquals("test", pair.second);
    }

    @Test
    public void useNestedLocationUrl() {
        String url = INSTANCE.toTestLocationUrl(INSTANCE.toPath(Arrays.asList("Nested.Module", "suite", "test")));
        String urlPath = url.substring(url.indexOf("://") + 3);

        Pair<String, String> pair = INSTANCE.fromLocationUrlPath(urlPath);
        assertEquals("tests/Nested/Module.elm", pair.first);
        assertEquals("suite/test", pair.second);
    }

    @Test
    public void useLocationUrlWithSlash() {
        String url = INSTANCE.toTestLocationUrl(INSTANCE.toPath(Arrays.asList("Module", "test / stuff")));
        String urlPath = url.substring(url.indexOf("://") + 3);

        Pair<String, String> pair = INSTANCE.fromLocationUrlPath(urlPath);
        assertEquals("tests/Module.elm", pair.first);
        assertEquals("test / stuff", pair.second);
    }

    @Test
    public void useLocationModuleOnly() {
        String url = INSTANCE.toTestLocationUrl(INSTANCE.toPath(Collections.singletonList("Module")));
        String urlPath = url.substring(url.indexOf("://") + 3);

        Pair<String, String> pair = INSTANCE.fromLocationUrlPath(urlPath);
        assertEquals("tests/Module.elm", pair.first);
        assertEquals("", pair.second);
    }

    @Test
    public void commonParentSameSuite() {
        Path from = INSTANCE.toPath(Arrays.asList("Module", "suite", "test"));
        Path to = INSTANCE.toPath(Arrays.asList("Module", "suite", "test2"));

        Path parent = INSTANCE.commonParent(from, to);
        assertEquals("Module/suite", INSTANCE.pathString(parent));
    }

    @Test
    public void commonParentDifferentSuite() {
        Path from = INSTANCE.toPath(Arrays.asList("Module", "suite", "test"));
        Path to = INSTANCE.toPath(Arrays.asList("Module", "suite2", "test2"));

        Path parent = INSTANCE.commonParent(from, to);
        assertEquals("Module", INSTANCE.pathString(parent));

        Path parent2 = INSTANCE.commonParent(to, from);
        assertEquals("Module", INSTANCE.pathString(parent2));
    }

    @Test
    public void commonParentDifferentSuite2() {
        Path from = INSTANCE.toPath(Arrays.asList("Module", "suite", "deep", "test"));
        Path to = INSTANCE.toPath(Arrays.asList("Module", "suite2", "test2"));

        Path parent = INSTANCE.commonParent(from, to);
        assertEquals("Module", INSTANCE.pathString(parent));

        Path parent2 = INSTANCE.commonParent(to, from);
        assertEquals("Module", INSTANCE.pathString(parent2));
    }

    @Test
    public void commonParentNoParent() {
        Path from = INSTANCE.toPath(Arrays.asList("Module", "suite", "test"));
        Path to = INSTANCE.toPath(Arrays.asList("Module2", "suite2", "test2"));

        Path parent = INSTANCE.commonParent(from, to);
        assertEquals("", INSTANCE.pathString(parent));
    }

    @Test
    public void parentPaths() {
        Path path = INSTANCE.toPath(Arrays.asList("Module", "suite", "test"));
        Path parent = INSTANCE.toPath(Collections.singletonList("Module"));

        List<String> parents = INSTANCE.subParents(path, parent)
                .map(LabelUtils.INSTANCE::pathString)
                .collect(Collectors.toList());
        assertEquals(Collections.singletonList("Module/suite"), parents);
    }

    @Test
    public void errorLocationUrl() {
        String url = INSTANCE.toErrorLocationUrl("my/path/file", 1313, 13);
        assertEquals("elmTestError://my/path/file::1313::13", url);

        String path = VirtualFileManager.extractPath(url);
        Pair<String, Pair<Integer, Integer>> pair = INSTANCE.fromErrorLocationUrlPath(path);

        String file = pair.first;
        int line = pair.second.first;
        int column = pair.second.second;

        assertEquals("my/path/file", file);
        assertEquals(1313, line);
        assertEquals(13, column);
    }

}