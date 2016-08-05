package com.vackosar.gitflowincrementalbuild.control;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.maven.execution.MavenSession;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.codehaus.plexus.logging.console.ConsoleLoggerManager;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.slf4j.impl.StaticLoggerBinder;

import com.google.inject.*;
import com.vackosar.gitflowincrementalbuild.boundary.Configuration;
import com.vackosar.gitflowincrementalbuild.boundary.GuiceModule;
import com.vackosar.gitflowincrementalbuild.mocks.*;

@RunWith(MockitoJUnitRunner.class)
public class DifferentFilesTest extends RepoTest {

    private static final String REFS_HEADS_FEATURE_2 = "refs/heads/feature/2";
    private static final String HEAD = "HEAD";
    private static final String FETCH_FILE = "fetch-file";
    private static final String DEVELOP = "refs/heads/develop";
    private static final String REMOTE_DEVELOP = "refs/remotes/origin/develop";
    private Path workDir;

    @Before
    public void before() throws Exception {
        super.init();
        workDir = LocalRepoMock.TEST_WORK_DIR.resolve("tmp/repo/");
        setWorkDir(workDir);
        localRepoMock = new LocalRepoMock(true);
    }

    @Test
    public void listIncludingUncommited() throws Exception {
        workDir.resolve("file5").toFile().createNewFile();
        Property.uncommited.setValue(Boolean.TRUE.toString());
        Assert.assertTrue(getInstance().get().stream().anyMatch(p -> p.toString().contains("file5")));
    }

    @Test
    public void listWithCheckout() throws Exception {
        getLocalRepoMock().getGit().reset().setRef(HEAD).setMode(ResetCommand.ResetType.HARD).call();
        Property.baseBranch.setValue("refs/heads/feature/2");
        getInstance().get();
        Assert.assertTrue(consoleOut.toString().contains("Checking out base branch refs/heads/feature/2"));
    }

    @Test
    public void list() throws Exception {
        final DifferentFiles differentFiles = getInstance();
        final Set<Path> expected = new HashSet<>(Arrays.asList(
                Paths.get(workDir + "/parent/child2/subchild2/src/resources/file2"),
                Paths.get(workDir + "/parent/child2/subchild2/src/resources/file22"),
                Paths.get(workDir + "/parent/child3/src/resources/file1"),
                Paths.get(workDir + "/parent/child4/pom.xml")
                ));
        Assert.assertEquals(expected, differentFiles.get());
    }

    @Test
    public void listInSubdir() throws Exception {
        Path workDir = LocalRepoMock.TEST_WORK_DIR.resolve("tmp/repo/parent/child2");
        setWorkDir(workDir);
        final DifferentFiles differentFiles = getInstance();
        final Set<Path> expected = new HashSet<>(Arrays.asList(
                workDir.resolve("subchild2/src/resources/file2"),
                workDir.resolve("subchild2/src/resources/file22"),
                workDir.resolve("../child3/src/resources/file1").normalize(),
                workDir.resolve("../child4/pom.xml").normalize()
        ));
        Assert.assertEquals(expected, differentFiles.get());
    }

    @Test
    public void listComparedToMergeBase() throws Exception {
        getLocalRepoMock().getGit().reset().setRef(HEAD).setMode(ResetCommand.ResetType.HARD).call();
        getLocalRepoMock().getGit().checkout().setName(REFS_HEADS_FEATURE_2).call();
        getLocalRepoMock().getGit().reset().setRef(HEAD).setMode(ResetCommand.ResetType.HARD).call();
        Property.baseBranch.setValue(REFS_HEADS_FEATURE_2);
        Property.compareToMergeBase.setValue("true");
        Assert.assertTrue(getInstance().get().stream().collect(Collectors.toSet()).contains(workDir.resolve("parent/feature2-only-file.txt")));
        Assert.assertTrue(consoleOut.toString().contains("59dc82fa887d9ca82a0d3d1790c6d767e738e71a"));
    }

    @Test
    public void fetch() throws Exception {
        Git remoteGit = localRepoMock.getRemoteRepo().getGit();
        remoteGit.reset().setMode(ResetCommand.ResetType.HARD).call();
        remoteGit.checkout().setName(DEVELOP).call();
        remoteGit.getRepository().getDirectory().toPath().resolve(FETCH_FILE).toFile().createNewFile();
        remoteGit.add().addFilepattern(".").call();
        remoteGit.commit().setMessage(FETCH_FILE).call();
        Assert.assertEquals(FETCH_FILE, remoteGit.log().setMaxCount(1).call().iterator().next().getFullMessage());
        Property.fetchReferenceBranch.setValue(Boolean.TRUE.toString());
        Property.referenceBranch.setValue(REMOTE_DEVELOP);
        getInstance().get();
        Git localGit = localRepoMock.getGit();
        localGit.reset().setMode(ResetCommand.ResetType.HARD).call();
        localGit.checkout().setName(REMOTE_DEVELOP).call();
        Assert.assertEquals(FETCH_FILE, localGit.log().setMaxCount(1).call().iterator().next().getFullMessage());
    }

    @Test
    public void fetchNonExistent() throws Exception {
        Git remoteGit = localRepoMock.getRemoteRepo().getGit();
        remoteGit.reset().setMode(ResetCommand.ResetType.HARD).call();
        remoteGit.checkout().setName(DEVELOP).call();
        remoteGit.getRepository().getDirectory().toPath().resolve(FETCH_FILE).toFile().createNewFile();
        remoteGit.add().addFilepattern(".").call();
        remoteGit.commit().setMessage(FETCH_FILE).call();
        Git localGit = localRepoMock.getGit();
        localGit.branchDelete().setBranchNames(DEVELOP).call();
        localGit.branchDelete().setBranchNames(REMOTE_DEVELOP).call();
        Assert.assertEquals(FETCH_FILE, remoteGit.log().setMaxCount(1).call().iterator().next().getFullMessage());
        Property.fetchReferenceBranch.setValue(Boolean.TRUE.toString());
        Property.referenceBranch.setValue(REMOTE_DEVELOP);
        getInstance().get();
        localGit.reset().setMode(ResetCommand.ResetType.HARD).call();
        localGit.checkout().setName(REMOTE_DEVELOP).call();
        Assert.assertEquals(FETCH_FILE, localGit.log().setMaxCount(1).call().iterator().next().getFullMessage());
    }


    private boolean filterIgnored(Path p) {
        return ! p.toString().contains("target") && ! p.toString().contains(".iml");
    }

    private ModuleFacade module() throws Exception {
        return new ModuleFacade();
    }

    private static class ModuleFacade extends AbstractModule {
        private final GuiceModule guiceModule;

        public ModuleFacade() throws Exception {
            this.guiceModule = new GuiceModule(new ConsoleLogger(), getMavenSessionMock());
        }

        @Singleton @Provides public Logger provideLogger() {
            return new ConsoleLoggerManager().getLoggerForComponent("Test");
        }

        @Singleton @Provides public Git provideGit() throws IOException, GitAPIException {
            return guiceModule.provideGit(new StaticLoggerBinder(new ConsoleLoggerManager().getLoggerForComponent("Test")));
        }

        @Singleton @Provides public Configuration arguments() throws Exception {
            MavenSession mavenSession = getMavenSessionMock();
            return new Configuration(mavenSession);
        }

        private MavenSession getMavenSessionMock() throws Exception {
            return MavenSessionMock.get();
        }

        @Override
        protected void configure() {}
    }

    private DifferentFiles getInstance() throws Exception {
        return Guice.createInjector(module()).getInstance(DifferentFiles.class);
    }

    private void setWorkDir(final Path path) {
        System.setProperty("user.dir", path.toString());
    }
}
