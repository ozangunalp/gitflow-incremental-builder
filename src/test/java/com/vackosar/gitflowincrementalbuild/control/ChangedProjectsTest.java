package com.vackosar.gitflowincrementalbuild.control;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.junit.Assert;
import org.junit.Test;

import com.google.inject.Guice;
import com.vackosar.gitflowincrementalbuild.boundary.GuiceModule;
import com.vackosar.gitflowincrementalbuild.mocks.*;

public class ChangedProjectsTest extends RepoTest {

    @Test
    public void list() throws Exception {
        final Set<Path> expected = new HashSet<>(Arrays.asList(
                Paths.get("child2/subchild2"),
                Paths.get("child3"),
                Paths.get("child4")
        ));
        final Set<Path> actual = Guice.createInjector(new GuiceModule(new ConsoleLogger(), MavenSessionMock.get()))
                .getInstance(ChangedProjects.class).get().stream()
                .map(MavenProject::getBasedir).map(File::toPath).map(LocalRepoMock.WORK_DIR.resolve("parent")::relativize).collect(Collectors.toSet());
        Assert.assertEquals(expected, actual);
    }
}
