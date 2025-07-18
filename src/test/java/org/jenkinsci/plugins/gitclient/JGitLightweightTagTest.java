package org.jenkinsci.plugins.gitclient;

import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.io.FileMatchers.anExistingDirectory;

import hudson.EnvVars;
import hudson.model.TaskListener;
import hudson.plugins.git.GitObject;
import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;

/**
 * JGit Client-specific test of lightweight tag. In their own test file to
 * simplify the test.
 *
 * @author Brian Ray
 */
public class JGitLightweightTagTest {
    /* These tests are only for the JGit client. */
    private static final String GIT_IMPL_NAME = "jgit";

    /* Instance under test. */
    private GitClient gitClient;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File repoRoot; // root directory of temporary repository
    private File repoRootGitDir; // .git directory in temporary repository

    @Before
    public void setGitClientEtc() throws Exception {
        repoRoot = tempFolder.newFolder();
        gitClient = Git.with(TaskListener.NULL, new EnvVars())
                .in(repoRoot)
                .using(GIT_IMPL_NAME)
                .getClient();
        repoRootGitDir = gitClient.withRepository((r, channel) -> r.getDirectory());
        gitClient.init_().workspace(repoRoot.getAbsolutePath()).execute();
        CliGitCommand gitCmd = new CliGitCommand(gitClient);
        gitCmd.initializeRepository();
        assertThat(repoRootGitDir, is(anExistingDirectory()));
    }

    private ObjectId commitFile(final String path, final String content, final String commitMessage) throws Exception {
        createFile(path, content);
        gitClient.add(path);
        gitClient.commit(commitMessage);

        List<ObjectId> headList = gitClient.revList(HEAD);
        assertThat(headList.size(), is(greaterThan(0)));
        return headList.get(0);
    }

    private void createFile(final String path, final String content) throws Exception {
        File aFile = new File(repoRoot, path);
        File parentDir = aFile.getParentFile();
        if (parentDir != null) {
            parentDir.mkdirs();
        }
        try (PrintWriter writer = new PrintWriter(aFile, StandardCharsets.UTF_8)) {
            writer.printf(content);
        }
    }

    private void packRefs() throws Exception {
        try (org.eclipse.jgit.api.Git jgit = new org.eclipse.jgit.api.Git(new FileRepository(repoRootGitDir))) {
            jgit.packRefs().setAll(true).call();
        }
    }

    // No flavor of GitClient has a tag(String) API, only tag(String,String).
    // But sometimes we want a lightweight a.k.a. non-annotated tag.
    private void lightweightTag(String tagName) throws Exception {
        try (FileRepository repo = new FileRepository(repoRootGitDir)) {
            org.eclipse.jgit.api.Git jgitAPI = org.eclipse.jgit.api.Git.wrap(repo);
            jgitAPI.tag().setName(tagName).setAnnotated(false).call();
        }
    }

    @Issue("JENKINS-57205") // NPE on PreBuildMerge with packed lightweight tag
    @Test
    public void testGetTags_packedRefs() throws Exception {
        // JENKINS-57205 is triggered by lightweight tags
        ObjectId firstCommit = commitFile("first.txt", "Great info here", "First commit");
        String lightweightTagName = "lightweight_tag";
        lightweightTag(lightweightTagName);

        // But throw in an annotated tag for symmetry and coverage
        ObjectId secondCommit = commitFile("second.txt", "Great info here, too", "Second commit");
        String annotatedTagName = "annotated_tag";
        gitClient.tag(annotatedTagName, "Tag annotation");

        // Must pack the tags and other refs in order to show the JGitAPIImpl bug
        packRefs();

        Set<GitObject> tags = gitClient.getTags();

        assertThat(
                tags,
                hasItem(allOf(
                        hasProperty("name", equalTo(lightweightTagName)), hasProperty("SHA1", equalTo(firstCommit)))));
        assertThat(
                tags,
                hasItem(allOf(
                        hasProperty("name", equalTo(annotatedTagName)), hasProperty("SHA1", equalTo(secondCommit)))));
        assertThat(tags, hasSize(2));
    }
}
