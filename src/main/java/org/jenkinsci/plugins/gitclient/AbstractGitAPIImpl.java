package org.jenkinsci.plugins.gitclient;

import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import hudson.FilePath;
import hudson.ProxyConfiguration;
import hudson.plugins.git.GitException;
import hudson.remoting.Channel;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.io.Writer;
import java.nio.charset.Charset;
import org.eclipse.jgit.internal.storage.file.WindowCache;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.storage.file.WindowCacheConfig;

/**
 * Common parts between {@link JGitAPIImpl} and {@link CliGitAPIImpl}.
 *
 * @author Kohsuke Kawaguchi
 */
abstract class AbstractGitAPIImpl implements GitClient, Serializable {
    /** {@inheritDoc} */
    @Override
    public <T> T withRepository(RepositoryCallback<T> callable) throws GitException, IOException, InterruptedException {
        try (Repository repo = getRepository()) {
            return callable.invoke(repo, FilePath.localChannel);
        } finally {
            // TODO Avoid JGit 7.2.0 and 7.3.0 file handle leak
            RepositoryCache.clear();
            WindowCache.reconfigure(new WindowCacheConfig());
        }
    }

    /** {@inheritDoc} */
    @Deprecated
    @Override
    public void commit(String message, PersonIdent author, PersonIdent committer)
            throws GitException, InterruptedException {
        setAuthor(author);
        setCommitter(committer);
        commit(message);
    }

    /** {@inheritDoc} */
    @Override
    public void setAuthor(PersonIdent p) throws GitException {
        if (p != null) {
            setAuthor(p.getName(), p.getEmailAddress());
        }
    }

    /** {@inheritDoc} */
    @Override
    public void setCommitter(PersonIdent p) throws GitException {
        if (p != null) {
            setCommitter(p.getName(), p.getEmailAddress());
        }
    }

    /** {@inheritDoc} */
    @Deprecated
    @Override
    public void changelog(String revFrom, String revTo, OutputStream outputStream)
            throws GitException, InterruptedException {
        changelog(revFrom, revTo, new OutputStreamWriter(outputStream, Charset.defaultCharset()));
    }

    /** {@inheritDoc} */
    @Override
    public void changelog(String revFrom, String revTo, Writer w) throws GitException, InterruptedException {
        changelog().excludes(revFrom).includes(revTo).to(w).execute();
    }

    /** {@inheritDoc} */
    @Override
    public void clone(String url, String origin, boolean useShallowClone, String reference)
            throws GitException, InterruptedException {
        CloneCommand c = clone_().url(url).repositoryName(origin).reference(reference);
        if (useShallowClone) {
            c.shallow(true);
        }
        c.execute();
    }

    /** {@inheritDoc} */
    @Deprecated
    @Override
    public void checkout(String commit) throws GitException, InterruptedException {
        checkout().ref(commit).execute();
    }

    /** {@inheritDoc} */
    @Deprecated
    @Override
    public void checkout(String ref, String branch) throws GitException, InterruptedException {
        checkout().ref(ref).branch(branch).execute();
    }

    /** {@inheritDoc} */
    @Override
    public void checkoutBranch(String branch, String ref) throws GitException, InterruptedException {
        checkout().ref(ref).branch(branch).deleteBranchIfExist(true).execute();
    }

    /** {@inheritDoc} */
    @Deprecated
    @Override
    public void merge(ObjectId rev) throws GitException, InterruptedException {
        merge().setRevisionToMerge(rev).execute();
    }

    /**
     * When sent to remote, switch to the proxy.
     *
     * @return a {@link java.lang.Object} object.
     * @throws java.io.ObjectStreamException if current channel is null
     */
    protected Object writeReplace() throws java.io.ObjectStreamException {
        Channel currentChannel = Channel.current();
        if (currentChannel == null) {
            throw new java.io.WriteAbortedException("No current channel", new java.lang.NullPointerException());
        }
        return remoteProxyFor(currentChannel.export(GitClient.class, this));
    }

    /**
     * remoteProxyFor.
     *
     * @param proxy a {@link org.jenkinsci.plugins.gitclient.GitClient} object.
     * @return a {@link org.jenkinsci.plugins.gitclient.RemoteGitImpl} object.
     */
    protected RemoteGitImpl remoteProxyFor(GitClient proxy) {
        return new RemoteGitImpl(proxy);
    }

    /** {@inheritDoc} */
    @Override
    public void setCredentials(StandardUsernameCredentials cred) {
        clearCredentials();
        addDefaultCredentials(cred);
    }

    protected ProxyConfiguration proxy;

    /** {@inheritDoc} */
    @Override
    public void setProxy(ProxyConfiguration proxy) {
        this.proxy = proxy;
    }

    /** {@inheritDoc} */
    @Deprecated
    @Override
    public void submoduleUpdate(boolean recursive) throws GitException, InterruptedException {
        submoduleUpdate().recursive(recursive).execute();
    }
    /** {@inheritDoc} */
    @Deprecated
    @Override
    public void submoduleUpdate(boolean recursive, String reference) throws GitException, InterruptedException {
        submoduleUpdate().recursive(recursive).ref(reference).execute();
    }
    /** {@inheritDoc} */
    @Deprecated
    @Override
    public void submoduleUpdate(boolean recursive, boolean remoteTracking) throws GitException, InterruptedException {
        submoduleUpdate().recursive(recursive).remoteTracking(remoteTracking).execute();
    }
    /** {@inheritDoc} */
    @Deprecated
    @Override
    public void submoduleUpdate(boolean recursive, boolean remoteTracking, String reference)
            throws GitException, InterruptedException {
        submoduleUpdate()
                .recursive(recursive)
                .remoteTracking(remoteTracking)
                .ref(reference)
                .execute();
    }
}
