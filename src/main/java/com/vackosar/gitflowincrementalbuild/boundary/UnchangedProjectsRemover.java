package com.vackosar.gitflowincrementalbuild.boundary;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.jgit.api.errors.GitAPIException;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.vackosar.gitflowincrementalbuild.control.ChangedProjects;

@Singleton
public class UnchangedProjectsRemover {

    private static final String MAVEN_TEST_SKIP = "maven.test.skip";

    @Inject private Configuration configuration;
    @Inject private Logger logger;
    @Inject private ChangedProjects changedProjects;
    @Inject private MavenSession mavenSession;

    public void act() throws GitAPIException, IOException {
        Set<MavenProject> changed = changedProjects.get();
        printDelimiter();
        logProjects(changed, "Changed Artifacts:");
        Set<MavenProject> changedProjects = new HashSet<>();
        mavenSession.getProjects().stream()
                        .filter(changed::contains)
                        .peek(m -> logger.warn(m.getId()))
                        .forEach(p -> getAllDependents(mavenSession.getProjects(), p, changedProjects));
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(
                        new FileOutputStream(mavenSession.getCurrentProject().getBasedir().getPath() + "/changed" +
                                        ".projects"), "utf-8"))) {
            for (MavenProject changedProject : changedProjects) {
                writer.write(changedProject.getGroupId()+":"+changedProject.getArtifactId());
                writer.write("\n");
            }
        }

        if (!configuration.buildAll) {
            Set<MavenProject> rebuildProjects = getRebuildProjects(changedProjects);
            if (rebuildProjects.isEmpty()) {
                logger.info("No changed artifacts to build. Executing validate goal only.");
                mavenSession.getGoals().clear();
                mavenSession.getGoals().add("validate");
            } else {
                mavenSession.setProjects(new ArrayList<>(rebuildProjects));
            }
        } else {
            mavenSession.getProjects().stream()
                    .filter(p -> !changedProjects.contains(p))
                    .forEach(this::ifSkipDependenciesTest);
        }
    }

    private Set<MavenProject> getRebuildProjects(Set<MavenProject> changedProjects) {
        if (configuration.makeUpstream) {
            return Stream.concat(changedProjects.stream(), collectDependencies(changedProjects)).collect(Collectors.toSet());
        } else {
            return changedProjects;
        }
    }

    private Stream<MavenProject> collectDependencies(Set<MavenProject> changedProjects) {
        return changedProjects.stream()
                .flatMap(this::ifMakeUpstreamGetDependencies)
                .filter(p -> ! changedProjects.contains(p))
                .map(this::ifSkipDependenciesTest);
    }

    private MavenProject ifSkipDependenciesTest(MavenProject mavenProject) {
        if (configuration.skipTestsForNotImpactedModules) {
            mavenProject.getProperties().setProperty(MAVEN_TEST_SKIP, Boolean.TRUE.toString());
        }
        return mavenProject;
    }

    private void logProjects(Collection<MavenProject> projects, String title) {
        logger.info(title);
        logger.info("");
        projects.stream().map(MavenProject::getArtifactId).forEach(logger::info);
        logger.info("");
    }

    private void printDelimiter() {
        logger.info("------------------------------------------------------------------------");
    }

    private void getAllDependents(List<MavenProject> projects, MavenProject project, Set<MavenProject> dependents) {
        dependents.add(project);
        for (MavenProject possibleDependent: projects) {
            if (isDependentOf(possibleDependent, project) || project.equals(possibleDependent.getParent())) {
                if (!dependents.contains(possibleDependent)) {
                    dependents.add(possibleDependent);
                    getAllDependents(projects, possibleDependent, dependents);
                }
            }
        }
    }

    private Stream<MavenProject> ifMakeUpstreamGetDependencies(MavenProject mavenProject) {
        return getAllDependencies(mavenSession.getProjects(), mavenProject).stream();
    }

    private Set<MavenProject> getAllDependencies(List<MavenProject> projects, MavenProject project) {
        Set<MavenProject> dependencies = project.getDependencies().stream()
                .map(d -> convert(projects, d)).filter(Optional::isPresent).map(Optional::get)
                .flatMap(p -> getAllDependencies(projects, p).stream())
                .collect(Collectors.toSet());
        dependencies.add(project);
        return dependencies;
    }

    private boolean equals(MavenProject project, Dependency dependency) {
        return dependency.getArtifactId().equals(project.getArtifactId())
                && dependency.getGroupId().equals(project.getGroupId())
                && dependency.getVersion().equals(project.getVersion());
    }

    private Optional<MavenProject> convert(List<MavenProject> projects, Dependency dependency) {
        return projects.stream().filter(p -> equals(p, dependency)).findFirst();
    }

    private boolean isDependentOf(MavenProject possibleDependent, MavenProject project) {
        return possibleDependent.getDependencies().stream().anyMatch(d -> equals(project, d));
    }
}
