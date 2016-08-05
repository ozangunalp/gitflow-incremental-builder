package com.vackosar.gitflowincrementalbuild.control;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.jgit.api.errors.GitAPIException;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class ChangedProjects {

    @Inject private Logger logger;
    @Inject private DifferentFiles differentFiles;
    @Inject private Modules modules;

    public Set<MavenProject> get() throws GitAPIException, IOException {
        return differentFiles.get().stream()
                .map(this::findProject)
                .filter(project -> project != null)
                .collect(Collectors.toSet());
    }

    private MavenProject findProject(Path diffPath) {
        Map<Path, MavenProject> map = modules.createPathMap();
        Path path = diffPath;
        while (path != null && ! map.containsKey(path)) {
            path = path.getParent();
        }
        if (path != null) {
            return map.get(path);
        } else {
            logger.debug("File changed outside build project: " + diffPath);
            return null;
        }
    }
}
