package com.vackosar.gitflowincrementalbuild.mocks;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;

public class MavenSessionMock {

    public static MavenSession get() throws Exception {
        List<MavenProject> projects = Stream.of(
                RepoTest.LOCAL_DIR.resolve("parent"),
                RepoTest.LOCAL_DIR.resolve("parent/child1"),
                RepoTest.LOCAL_DIR.resolve("parent/child2"),
                RepoTest.LOCAL_DIR.resolve("parent/child2/subchild1"),
                RepoTest.LOCAL_DIR.resolve("parent/child2/subchild2"),
                RepoTest.LOCAL_DIR.resolve("parent/child3"),
                RepoTest.LOCAL_DIR.resolve("parent/child4"),
                RepoTest.LOCAL_DIR.resolve("parent/child4/subchild41"),
                RepoTest.LOCAL_DIR.resolve("parent/child4/subchild42"),
                RepoTest.LOCAL_DIR.resolve("parent/child5")
        ).map(MavenSessionMock::createProject).collect(Collectors.toList());
        MavenSession mavenSession = mock(MavenSession.class);
        when(mavenSession.getCurrentProject()).thenReturn(projects.get(0));
        MavenExecutionRequest request = mock(MavenExecutionRequest.class);
        when(mavenSession.getRequest()).thenReturn(request);
        when(mavenSession.getUserProperties()).thenReturn(new Properties());
        when(mavenSession.getProjects()).thenReturn(projects);
        when(mavenSession.getTopLevelProject()).thenReturn(projects.get(0));
        return mavenSession;
    }

    private static MavenProject createProject(Path path) {
        MavenProject project = new MavenProject();
        Model model = new Model();
        model.setProperties(new Properties());
        project.setModel(model);
        project.setArtifactId(path.getFileName().toString());
        project.setGroupId(path.getFileName().toString());
        project.setVersion("1");
        project.setFile(path.resolve("pom.xml").toFile());
        return project;
    }
}
