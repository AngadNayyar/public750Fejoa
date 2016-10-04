/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.database;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.Merger;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.fejoa.chunkstore.*;
import org.fejoa.library.crypto.CryptoException;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;


public class JGitInterface implements IDatabaseInterface {
    private Repository repository = null;
    private String path = "";
    private String branch = "";
    private ObjectId rootTree = ObjectId.zeroId();

    public void init(String path, String branch, boolean create) throws IOException {
        this.path = path;
        this.branch = branch;
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        repository = builder.setGitDir(new File(path))
                .readEnvironment()
                .findGitDir()
                .build();

        File dir = new File(path);
        if (!dir.exists()) {
            if (create)
                repository.create(create);
            else
                return;
        }

        // get root tree if existing
        rootTree = getTipTree();
    }

    private ObjectId getTipTree() throws IOException {
        // get root tree if existing
        Ref branchRef = repository.getRef(branch);
        if (branchRef == null)
            return ObjectId.zeroId();
        RevWalk walk = new RevWalk(repository);
        RevCommit commit = walk.parseCommit(branchRef.getLeaf().getObjectId());
        if (commit == null)
            throw new IOException("no commit in head!");
        return commit.getTree().toObjectId();
    }

    public String getPath() {
        return path;
    }

    @Override
    public String getBranch() {
        return branch;
    }

    public void write(String path, long length, InputStream stream) throws IOException {
        // first remove old entry if there is any
        try {
            rmFile(path);
        } catch(IOException e) {

        }

        // write the blob
        final ObjectInserter objectInserter = repository.newObjectInserter();
        final ObjectId blobId = objectInserter.insert(Constants.OBJ_BLOB, length, stream);

        final DirCache cache = DirCache.newInCore();
        final DirCacheBuilder builder = cache.builder();
        if (!rootTree.equals(ObjectId.zeroId())) {
            final ObjectReader reader = repository.getObjectDatabase().newReader();
            builder.addTree("".getBytes(), DirCacheEntry.STAGE_0, reader, rootTree);
        }
        final DirCacheEntry entry = new DirCacheEntry(path);

        entry.setLastModified(System.currentTimeMillis());

        entry.setFileMode(FileMode.REGULAR_FILE);
        entry.setObjectId(blobId);

        builder.add(entry);
        builder.finish();

        rootTree = cache.writeTree(objectInserter);
        objectInserter.flush();
    }

    public Repository getRepository() {
        return repository;
    }

    @Override
    public boolean hasFile(String path) throws IOException, CryptoException {
        try {
            readBytes(path);
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    @Override
    public byte[] readBytes(String path) throws IOException{
        return repository.open(getObject(path)).getBytes();
    }

    @Override
    public HashValue getHash(String path) throws IOException {
        return HashValue.fromHex(getObject(path).getName());
    }

    private ObjectId getObject(String path) throws IOException {
        TreeWalk treeWalk = cdFile(path);
        if (!treeWalk.next())
            throw new FileNotFoundException();

        return treeWalk.getObjectId(0);
    }

    @Override
    public void writeBytes(String path, byte[] bytes) throws IOException {
        // first remove old entry if there is any
        try {
            rmFile(path);
        } catch(IOException e) {

        }

        // write the blob
        final ObjectInserter objectInserter = repository.newObjectInserter();
        final ObjectId blobId = objectInserter.insert(Constants.OBJ_BLOB, bytes);

        final DirCache cache = DirCache.newInCore();
        final DirCacheBuilder builder = cache.builder();
        if (!rootTree.equals(ObjectId.zeroId())) {
            final ObjectReader reader = repository.getObjectDatabase().newReader();
            builder.addTree("".getBytes(), DirCacheEntry.STAGE_0, reader, rootTree);
        }
        final DirCacheEntry entry = new DirCacheEntry(path);

        entry.setLastModified(System.currentTimeMillis());

        entry.setFileMode(FileMode.REGULAR_FILE);
        entry.setObjectId(blobId);

        builder.add(entry);
        builder.finish();

        rootTree = cache.writeTree(objectInserter);
        objectInserter.flush();
    }

    public HashValue commit() throws IOException {
        return commit("", null);
    }

    @Override
    public HashValue commit(String message, ICommitSignature commitSignature) throws IOException {
        if (rootTree.equals(ObjectId.zeroId()))
            throw new InvalidObjectException("invalid root tree");

        CommitBuilder commit = new CommitBuilder();
        PersonIdent personIdent = new PersonIdent("client", "");
        commit.setCommitter(personIdent);
        commit.setAuthor(personIdent);
        commit.setMessage(message);
        commit.setTreeId(rootTree);
        HashValue tip = getTip();
        ObjectId oldTip;
        if (tip.isZero())
            oldTip = ObjectId.zeroId();
        else {
            oldTip = ObjectId.fromString(tip.toHex());
            commit.setParentId(oldTip);
        }

        ObjectInserter objectInserter = repository.newObjectInserter();
        ObjectId commitId = objectInserter.insert(commit);
        objectInserter.flush();

        RefUpdate refUpdate = repository.updateRef("refs/heads/" + getBranch());
        refUpdate.setForceUpdate(true);
        refUpdate.setRefLogIdent(personIdent);
        refUpdate.setNewObjectId(commitId);
        refUpdate.setExpectedOldObjectId(oldTip);
        refUpdate.setRefLogMessage("client commit", false);
        RefUpdate.Result result = refUpdate.update();
        if (result == RefUpdate.Result.REJECTED)
            throw new IOException();

        return HashValue.fromHex(commitId.name());
    }

    private TreeWalk cdFile(String path) throws IOException {
        RevWalk walk = new RevWalk(repository);
        ObjectId tree;
        if (!rootTree.equals(ObjectId.zeroId()))
            tree = rootTree;
        else {
            Ref branchRef = repository.getRef(branch);
            if (branchRef == null)
                throw new IOException();
            RevCommit commit = walk.parseCommit(branchRef.getLeaf().getObjectId());
            if (commit == null)
                throw new IOException();
            tree = commit.getTree().toObjectId();
        }
        TreeWalk treeWalk = new TreeWalk(repository);
        treeWalk.addTree(tree);
        treeWalk.setRecursive(true);
        treeWalk.setFilter(PathFilter.create(path));

        return treeWalk;
    }

    private TreeWalk cd(String path) throws IOException {
        RevWalk walk = new RevWalk(repository);
        ObjectId tree;
        if (!rootTree.equals(ObjectId.zeroId()))
            tree = rootTree;
        else {
            Ref branchRef = repository.getRef(branch);
            if (branchRef == null)
                throw new IOException();
            RevCommit commit = walk.parseCommit(branchRef.getLeaf().getObjectId());
            if (commit == null)
                throw new IOException();
            tree = commit.getTree().toObjectId();
        }
        TreeWalk treeWalk = new TreeWalk(repository);
        treeWalk.addTree(tree);
        treeWalk.setRecursive(true);
        if (!path.equals("") && !path.equals("/"))
            treeWalk.setFilter(PathFilter.create(path));
        return treeWalk;
    }

    @Override
    public Collection<String> listFiles(String path) throws IOException {
        List<String> files = new ArrayList<>();

        TreeWalk treeWalk = cd(path);

        String pathList[] = path.split("/");
        int nDirs = 0;
        if (!path.equals("") && !path.equals("/"))
            nDirs = pathList.length;
        while (treeWalk.next()) {
            if (treeWalk.isSubtree())
                continue;
            String currentPath = treeWalk.getPathString();
            String currentPathList[] = currentPath.split("/");
            if (currentPathList.length == nDirs + 1)
                files.add(treeWalk.getNameString());
        }

        return files;
    }

    @Override
    public Collection<String> listDirectories(String path) throws IOException {
        // TODO: this is horrible slow!!
        List<String> dirs = new ArrayList<>();

        TreeWalk treeWalk = cd(path);

        String pathList[] = path.split("/");
        int nDirs = 0;
        if (!path.equals("") && !path.equals("/"))
            nDirs = pathList.length;
        while (treeWalk.next()) {
            String currentPath = treeWalk.getPathString();
            String currentPathList[] = currentPath.split("/");
            int nParts = currentPathList.length;
            if (nParts - 1 > nDirs){
                String dir = currentPathList[nDirs];
                if (!dirs.contains(dir))
                    dirs.add(dir);
            }
        }

        return dirs;
    }

    @Override
    public HashValue getTip() throws IOException {
        Ref head = getHeadRef();
        if (head == null)
            return org.fejoa.chunkstore.Config.newSha1Hash();
        return HashValue.fromHex(head.getObjectId().name());
    }

    private Ref getHeadRef() throws IOException {
        return repository.getRef("refs/heads/" + branch);
    }

    private void updateTip(String commit) throws IOException {
        String refPath = getPath() + "/refs/heads/";
        refPath += branch;

        PrintWriter out = new PrintWriter(new FileWriter(new File(refPath), false));
        try {
            out.println(commit);
        } finally {
            out.close();
        }
    }

    private boolean needsCommit() throws IOException {
        if (getTipTree().equals(rootTree))
            return false;
        return true;
    }

    private String mergeCommit(ObjectId tree, RevCommit parent1, RevCommit parent2)
            throws IOException {
        CommitBuilder commit = new CommitBuilder();
        PersonIdent personIdent = new PersonIdent("Client", "");
        commit.setCommitter(personIdent);
        commit.setAuthor(personIdent);
        commit.setMessage("merge");
        commit.setTreeId(tree);
        commit.addParentId(parent1);
        commit.addParentId(parent2);
        HashValue tip = getTip();
        ObjectId oldTip;
        if (tip.isZero())
            oldTip = ObjectId.zeroId();
        else {
            oldTip = ObjectId.fromString(tip.toHex());
        }

        ObjectInserter objectInserter = repository.newObjectInserter();
        ObjectId commitId = objectInserter.insert(commit);
        objectInserter.flush();

        RefUpdate refUpdate = repository.updateRef("refs/heads/" + getBranch());
        refUpdate.setForceUpdate(true);
        refUpdate.setRefLogIdent(personIdent);
        refUpdate.setNewObjectId(commitId);
        refUpdate.setExpectedOldObjectId(oldTip);
        refUpdate.setRefLogMessage("client commit", false);
        RefUpdate.Result result = refUpdate.update();
        if (result == RefUpdate.Result.REJECTED)
            throw new IOException();

        return commitId.name();
    }

    public void merge(HashValue theirCommitId) throws IOException {
        if (theirCommitId.isZero())
            return;
        // commit if necessary
        if (needsCommit())
            commit("", null);

        ObjectId theirs = ObjectId.fromString(theirCommitId.toHex());
        RevCommit theirsCommit = new RevWalk(repository).parseCommit(theirs);

        Ref headRef = getHeadRef();
        if (headRef == null) {
            rootTree = theirsCommit.getTree();

            // we have no commit; just set tip to their commit
            RefUpdate refUpdate = repository.updateRef("refs/heads/" + getBranch());
            refUpdate.setForceUpdate(true);
            refUpdate.setRefLogIdent(new PersonIdent("client", ""));
            refUpdate.setNewObjectId(theirsCommit);
            refUpdate.setExpectedOldObjectId(ObjectId.zeroId());
            refUpdate.setRefLogMessage("client commit", false);
            RefUpdate.Result result = refUpdate.update();
            if (result == RefUpdate.Result.REJECTED)
                throw new IOException();
            return;
        }
        ObjectId ours = getHeadRef().getObjectId();
        if (ours.equals(theirs))
            return;
        RevCommit oursCommit = new RevWalk(repository).parseCommit(headRef.getObjectId());

        Merger merger = MergeStrategy.SIMPLE_TWO_WAY_IN_CORE.newMerger(repository);
        boolean merged = merger.merge(ours, theirs);
        if (!merged)
            throw new IOException("can't merge");
        rootTree = merger.getResultTreeId();
        if (needsCommit()) {
            //TODO check why this is not working!
            // create a normal commit if the merged tree is equal their's
            //if (rootTree.equals(theirsCommit.getTree()))
              //  commit();
            //else
                mergeCommit(rootTree, oursCommit, theirsCommit);
        }
    }

    @Override
    public void remove(String path) throws IOException {
        /*if (isDirectory(path))
            rmDirectory(path);
        else
            rmFile(path);*/
        //todo isDirectory is not always working, see testRemove
        rmFile(path);
        rmDirectory(path);
    }

    @Override
    public DatabaseDiff getDiff(HashValue baseCommit, HashValue endCommit) throws IOException {
        if (baseCommit.isZero()) {
            // list commit tree

            RevWalk walk = new RevWalk(repository);
            RevCommit commit = walk.parseCommit(ObjectId.fromString(endCommit.toHex()));
            walk.dispose();

            DatabaseDiff databaseDiff = new DatabaseDiff();

            TreeWalk treeWalk = new TreeWalk(repository);
            treeWalk.addTree(commit.getTree());
            treeWalk.setRecursive(false);
            while (treeWalk.next()) {
                if (treeWalk.isSubtree())
                    treeWalk.enterSubtree();
                else
                    databaseDiff.added.addPath(treeWalk.getPathString());
            }

            return databaseDiff;
        }
        AbstractTreeIterator baseTree = prepareTreeParser(repository, baseCommit.toHex());
        AbstractTreeIterator newTree = prepareTreeParser(repository, endCommit.toHex());

        List<DiffEntry> diff;
        try {
            diff = new Git(repository).diff().setOldTree(baseTree).setNewTree(newTree).call();
        } catch (GitAPIException e) {
            e.printStackTrace();
            throw new IOException(e.getMessage());
        }

        DatabaseDiff databaseDiff = new DatabaseDiff();
        for (DiffEntry entry : diff) {
            switch (entry.getChangeType()) {
                case ADD:
                    databaseDiff.added.addPath(entry.getNewPath());
                    break;

                case MODIFY:
                    databaseDiff.modified.addPath(entry.getNewPath());
                    break;

                case DELETE:
                    databaseDiff.removed.addPath(entry.getOldPath());
                    break;

                case RENAME:
                case COPY:
                    break;
            }
        }

        return databaseDiff;
    }

    private static AbstractTreeIterator prepareTreeParser(Repository repository, String commitId) throws IOException {
        RevWalk walk = new RevWalk(repository);
        RevCommit commit = walk.parseCommit(ObjectId.fromString(commitId));
        RevTree tree = walk.parseTree(commit.getTree().getId());
        CanonicalTreeParser oldTreeParser = new CanonicalTreeParser();
        ObjectReader oldReader = repository.newObjectReader();
        try {
            oldTreeParser.reset(oldReader, tree.getId());
        } finally {
            oldReader.release();
        }
        walk.dispose();
        return oldTreeParser;
    }

    private boolean isDirectory(String path) throws IOException {
        TreeWalk treeWalk = cd(path);
        treeWalk.next();
        // it's a directory if there is a second entry
        return treeWalk.next();
    }

    private void rm(DirCacheEditor.PathEdit operation) throws IOException {
        final DirCache cache = DirCache.newInCore();
        final DirCacheBuilder builder = cache.builder();
        if (!rootTree.equals(ObjectId.zeroId())) {
            final ObjectReader reader = repository.getObjectDatabase().newReader();
            builder.addTree("".getBytes(), DirCacheEntry.STAGE_0, reader, rootTree);
        }
        builder.finish();

        final DirCacheEditor cacheEditor = cache.editor();
        cacheEditor.add(operation);
        cacheEditor.finish();

        final ObjectInserter objectInserter = repository.newObjectInserter();
        rootTree = cache.writeTree(objectInserter);
        objectInserter.flush();
    }

    private void rmFile(String filePath) throws IOException {
        rm(new DirCacheEditor.DeletePath(filePath));
    }

    private void rmDirectory(String path) throws IOException {
        rm(new DirCacheEditor.DeleteTree(path));
    }
}
