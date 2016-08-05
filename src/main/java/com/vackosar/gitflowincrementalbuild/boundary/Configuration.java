package com.vackosar.gitflowincrementalbuild.boundary;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.vackosar.gitflowincrementalbuild.control.Property;

@Singleton
public class Configuration {

    private static final String MAKE_UPSTREAM = "make-upstream";

    public final boolean enabled;
    public final Optional<Path> key;
    public final String referenceBranch;
    public final String baseBranch;
    public final boolean uncommited;
    public final boolean makeUpstream;
    public final boolean skipTestsForNotImpactedModules;
    public final boolean buildAll;
    public final boolean compareToMergeBase;
    public final boolean fetchBaseBranch;
    public final boolean fetchReferenceBranch;

    @Inject
    public Configuration(MavenSession session) throws IOException {
        try {
            mergeCurrentProjectProperties(session);
            checkProperties();
            enabled = Boolean.valueOf(Property.enabled.getValue());
            key = parseKey(session);
            referenceBranch = Property.referenceBranch.getValue();
            baseBranch = Property.baseBranch.getValue();
            uncommited = Boolean.valueOf(Property.uncommited.getValue());
            makeUpstream = MAKE_UPSTREAM.equals(session.getRequest().getMakeBehavior());
            skipTestsForNotImpactedModules = Boolean.valueOf(Property.skipTestsForNotImpactedModules.getValue());
            buildAll = Boolean.valueOf(Property.buildAll.getValue());
            compareToMergeBase = Boolean.valueOf(Property.compareToMergeBase.getValue());
            fetchReferenceBranch = Boolean.valueOf(Property.fetchReferenceBranch.getValue());
            fetchBaseBranch = Boolean.valueOf(Property.fetchBaseBranch.getValue());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Optional<Path> parseKey(MavenSession session) throws IOException {
        Path pomDir = session.getCurrentProject().getBasedir().toPath();
        String keyOptionValue = Property.repositorySshKey.getValue();
        if (keyOptionValue != null && ! keyOptionValue.isEmpty()) {
            return Optional.of(pomDir.resolve(keyOptionValue).toAbsolutePath().toRealPath().normalize());
        } else {
            return Optional.empty();
        }
    }

    private void mergeCurrentProjectProperties(MavenSession mavenSession) {
        mavenSession.getTopLevelProject().getProperties().entrySet().stream()
                .filter(e->e.getKey().toString().startsWith(Property.PREFIX))
                .filter(e->System.getProperty(e.getKey().toString()) == null)
                .forEach(e->System.setProperty(e.getKey().toString(), e.getValue().toString()));
    }

    private void checkProperties() throws MavenExecutionException {
        try {
            System.getProperties().entrySet().stream().map(Map.Entry::getKey)
                    .filter(o -> o instanceof String).map(o -> (String) o)
                    .filter(s -> s.startsWith(Property.PREFIX))
                    .map(s -> s.replaceFirst(Property.PREFIX, ""))
                    .forEach(Property::valueOf);
        } catch (IllegalArgumentException e) {
            throw new MavenExecutionException("Invalid invalid GIB property found. Allowed properties: \n" + Property.exemplifyAll(), e);
        }
    }
}
