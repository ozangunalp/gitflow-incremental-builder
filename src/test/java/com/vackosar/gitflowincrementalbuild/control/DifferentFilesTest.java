package com.vackosar.gitflowincrementalbuild.control;

import static com.vackosar.gitflowincrementalbuild.mocks.ModuleMock.module;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import com.google.inject.Guice;
import com.vackosar.gitflowincrementalbuild.mocks.LocalRepoMock;
import com.vackosar.gitflowincrementalbuild.mocks.RepoTest;

@RunWith(MockitoJUnitRunner.class)
public class DifferentFilesTest extends RepoTest {

    private static final String REFS_HEADS_FEATURE_2 = "refs/heads/feature/2";
    private static final String HEAD = "HEAD";
    private static final String FETCH_FILE = "fetch-file";
    private static final String DEVELOP = "refs/heads/develop";
    private static final String REMOTE_DEVELOP = "refs/remotes/origin/develop";

    @Before
    public void before() throws Exception {
        super.init();
        localRepoMock = new LocalRepoMock(true);
    }

    @Test
    public void listIncludingOnlyUncommited() throws Exception {
        LOCAL_DIR.resolve("file5").toFile().createNewFile();
        module().provideGit().add().addFilepattern(".").call();
        Property.untracked.setValue(Boolean.FALSE.toString());
        Property.uncommited.setValue(Boolean.TRUE.toString());
        Assert.assertTrue(getInstance().get().stream().anyMatch(p -> p.toString().contains("file5")));
    }

    @Test
    public void listIncludingOnlyUntracked() throws Exception {
        LOCAL_DIR.resolve("file5").toFile().createNewFile();
        Property.uncommited.setValue(Boolean.FALSE.toString());
        Property.untracked.setValue(Boolean.TRUE.toString());
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
                Paths.get(LOCAL_DIR + "/child2/subchild2/src/resources/file2"),
                Paths.get(LOCAL_DIR + "/child2/subchild2/src/resources/file22"),
                Paths.get(LOCAL_DIR + "/child3/src/resources/file1"),
                Paths.get(LOCAL_DIR + "/child4/pom.xml")
                ));
        Assert.assertEquals(expected, differentFiles.get());
    }

    @Test
    public void listInSubdir() throws Exception {
        Path workDir = LOCAL_DIR.resolve("child2");
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
        Assert.assertTrue(getInstance().get().stream()
                .collect(Collectors.toSet()).contains(LOCAL_DIR.resolve("feature2-only-file.txt")));
        Assert.assertTrue(consoleOut.toString().contains("2dae55f63194af320e51c49127ef41ce440b9f7b"));
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

    private DifferentFiles getInstance() throws Exception {
        return Guice.createInjector(module()).getInstance(DifferentFiles.class);
    }

}
