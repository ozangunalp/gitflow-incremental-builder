/*
 * Copyright (C) by Courtanet, All Rights Reserved.
 */
package com.vackosar.gitflowincrementalbuild.mojo;

import static com.vackosar.gitflowincrementalbuild.boundary.UnchangedProjectsRemover.CHANGED_PROJECTS;
import static com.vackosar.gitflowincrementalbuild.control.Property.PREFIX;
import static com.vackosar.gitflowincrementalbuild.utils.PluginUtils.joinProjectIds;
import static com.vackosar.gitflowincrementalbuild.utils.PluginUtils.writeChangedProjectsToFile;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.StringJoiner;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.*;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;
import org.eclipse.jgit.api.errors.GitAPIException;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.vackosar.gitflowincrementalbuild.boundary.*;
import com.vackosar.gitflowincrementalbuild.control.ChangedProjects;
import com.vackosar.gitflowincrementalbuild.utils.MavenToPlexusLogAdapter;

@Mojo(name = "writeChanged", defaultPhase = LifecyclePhase.VALIDATE,
                threadSafe = true, inheritByDefault = false, aggregator = true)
public class ChangedMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}")
    private MavenProject project;

    @Parameter(defaultValue = "${session}")
    private MavenSession session;

    @Parameter(required = false, property = PREFIX + "enabled", defaultValue = "true")
    public boolean enabled;

    @Parameter(required = false, property = PREFIX + "key", defaultValue = "")
    public String key;

    @Parameter(required = false, property = PREFIX + "referenceBranch", defaultValue = "refs/remotes/origin/develop")
    public String referenceBranch;

    @Parameter(required = false, property = PREFIX + "baseBranch", defaultValue = "HEAD")
    public String baseBranch;

    @Parameter(required = false, property = PREFIX + "uncommited", defaultValue = "true")
    public boolean uncommited;

    @Parameter(required = false, property = PREFIX + "makeUpstream", defaultValue = "false")
    public boolean makeUpstream;

    @Parameter(required = false, property = PREFIX + "skipTestsForNotImpactedModules", defaultValue = "false")
    public boolean skipTestsForNotImpactedModules;

    @Parameter(required = false, property = PREFIX + "buildAll", defaultValue = "false")
    public boolean buildAll;

    @Parameter(required = false, property = PREFIX + "compareToMergeBase", defaultValue = "true")
    public boolean compareToMergeBase;

    @Parameter(required = false, property = PREFIX + "fetchBaseBranch", defaultValue = "false")
    public boolean fetchBaseBranch;

    @Parameter(required = false, property = PREFIX + "fetchReferenceBranch", defaultValue = "false")
    public boolean fetchReferenceBranch;

    @Parameter(required = false, property = PREFIX + "outputFile", defaultValue = "${project.basedir}/changed.projects")
    public String outputFile;

    @Parameter(required = false, property = PREFIX + "writeChanged", defaultValue = "false")
    public String writeChanged;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        if (!project.isExecutionRoot()) {
            return;
        }

        Injector injector = Guice.createInjector(new GuiceModule(new MavenToPlexusLogAdapter(getLog()), session));
        UnchangedProjectsRemover projectsRemover = injector.getInstance(UnchangedProjectsRemover.class);
        ChangedProjects changedProjects = injector.getInstance(ChangedProjects.class);

        getLog().info(injector.getInstance(Configuration.class).toString());

        try {
            Set<MavenProject> changed = changedProjects.get();
            Set<MavenProject> allDependentProjects = projectsRemover.getAllDependentProjects(changed);
            writeChangedProjectsToFile(allDependentProjects, new File(outputFile));
            session.getAllProjects().forEach(m -> m.getProperties()
                            .setProperty(CHANGED_PROJECTS, joinProjectIds(changed, new StringJoiner(",")).toString()));
        } catch (GitAPIException | IOException e) {
            e.printStackTrace();
        }

    }

}
