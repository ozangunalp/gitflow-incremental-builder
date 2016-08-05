package com.vackosar.gitflowincrementalbuild.boundary;

import com.vackosar.gitflowincrementalbuild.control.Property;
import com.vackosar.gitflowincrementalbuild.mocks.LocalRepoMock;
import com.vackosar.gitflowincrementalbuild.mocks.RepoTest;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IT extends RepoTest {

    @Test
    public void buildDisabled() throws Exception {
        String output = executeBuild(Collections.singletonList(
                        "-Dgib." + Property.enabled + "=false"));
        System.out.println(output);
        Assert.assertTrue(output.contains("gitflow-incremental-builder is disabled."));
    }

    @Test
    public void buildAllSkipTest() throws Exception {
        final String output = executeBuild(Arrays.asList(
                        "-Dgib." + Property.buildAll + "=true",
                        "-Dgib." + Property.skipTestsForNotImpactedModules.name() + "=true"));
        System.out.println(output);

        Assert.assertTrue(output.contains(" child1"));
        Assert.assertTrue(output.contains(" child2"));
        Assert.assertTrue(output.contains(" subchild1"));
        Assert.assertTrue(output.contains(" subchild42"));
        Assert.assertTrue(output.contains(" subchild2"));
        Assert.assertTrue(output.contains(" child3"));
        Assert.assertTrue(output.contains(" child4"));
        Assert.assertTrue(output.contains(" subchild41"));
        Assert.assertTrue(output.contains(" child6"));
        Assert.assertTrue(output.contains("[INFO] Tests are skipped."));
    }

    @Test
    public void buildWithAlsoMake() throws Exception {
        final String output = executeBuild(Collections.singletonList("-am"));
        System.out.println(output);

        Assert.assertFalse(output.contains(" child1"));
        Assert.assertFalse(output.contains(" child2"));
        Assert.assertFalse(output.contains(" subchild1"));
        Assert.assertFalse(output.contains(" subchild42"));

        Assert.assertTrue(output.contains(" subchild2"));
        Assert.assertTrue(output.contains(" child3"));
        Assert.assertTrue(output.contains(" child4"));
        Assert.assertTrue(output.contains(" subchild41"));
        Assert.assertTrue(output.contains(" child6"));
    }

    @Test
    public void buildNoChanged() throws Exception {
        Git git = localRepoMock.getGit();
        git.reset().setMode(ResetCommand.ResetType.HARD).setRef("HEAD").call();
        git.checkout().setName("develop").call();
        final String output = executeBuild(Collections.singletonList("-Dgib." + Property.baseBranch.name() +
                        "=refs/heads/develop"));
        System.out.println(output);

        Assert.assertTrue(output.contains("Executing validate goal only."));
        Assert.assertTrue(output.contains(" child1"));
        Assert.assertTrue(output.contains(" child2"));
        Assert.assertTrue(output.contains(" subchild1"));
        Assert.assertTrue(output.contains(" subchild42"));
        Assert.assertTrue(output.contains(" subchild2"));
        Assert.assertTrue(output.contains(" child3"));
        Assert.assertTrue(output.contains(" child4"));
        Assert.assertTrue(output.contains(" subchild41"));
        Assert.assertTrue(output.contains(" child6"));
    }

    @Test
    public void buildWithAlsoMakeSkip() throws Exception {
        final String output = executeBuild(Arrays.asList(
                        "-am",
                        "-Dgib." + Property.skipTestsForNotImpactedModules.name() + "=true"));
        System.out.println(output);

        Assert.assertFalse(output.contains(" child1"));
        Assert.assertFalse(output.contains(" child2"));
        Assert.assertFalse(output.contains(" subchild1"));
        Assert.assertFalse(output.contains(" subchild42"));

        Assert.assertTrue(output.contains(" subchild2"));
        Assert.assertTrue(output.contains(" child3"));
        Assert.assertTrue(output.contains(" child4"));
        Assert.assertTrue(output.contains(" subchild41"));
        Assert.assertTrue(output.contains(" child6"));
        Assert.assertTrue(output.contains("[INFO] Tests are skipped."));
    }

    @Test
    public void buildWithoutAlsoMake() throws Exception {
        final String output = executeBuild(Collections.emptyList());
        System.out.println(output);

        Assert.assertFalse(output.contains(" child1"));
        Assert.assertFalse(output.contains(" child2"));
        Assert.assertFalse(output.contains(" subchild1"));
        Assert.assertFalse(output.contains(" subchild42"));
        Assert.assertFalse(output.contains(" child6"));

        Assert.assertTrue(output.contains(" subchild2"));
        Assert.assertTrue(output.contains(" child3"));
        Assert.assertTrue(output.contains(" child4"));
        Assert.assertTrue(output.contains(" subchild41"));
    }

    private String executeBuild(List<String> args) throws IOException, InterruptedException {
        final List<String> command = Arrays.asList("mvn", "install",
                        "--file", "parent/pom.xml",
                        "-DgibVersion=" + pluginVersion);
        final Process process = new ProcessBuilder(Stream.concat(command.stream(), args.stream())
                        .collect(Collectors.toList()))
                        .directory(LocalRepoMock.WORK_DIR.toFile().getAbsoluteFile())
                        .start();
        String output = convertStreamToString(process.getInputStream());
        System.out.println(convertStreamToString(process.getErrorStream()));
        process.waitFor();
        return output;
    }

    private static String convertStreamToString(java.io.InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }
}
