/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.downlink.ssh;

import sirius.biz.storage.layer3.FileSearch;
import sirius.biz.storage.layer3.VirtualFile;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Provides an interface between NIO and the <tt>FileSystem</tt> API torwards {@link VirtualFile#children(FileSearch)}.
 */
public class BridgeDirectoryStream implements DirectoryStream<Path> {

    private final VirtualFile virtualFile;
    private final BridgeFileSystem fileSystem;

    /**
     * Generates a wrapper for the given directory and file system.
     *
     * @param directory the directory to wrap
     * @param fileSystem the file system to pass along
     */
    public BridgeDirectoryStream(VirtualFile directory, BridgeFileSystem fileSystem) {
        this.virtualFile = directory;
        this.fileSystem = fileSystem;
    }

    @Override
    public Iterator<Path> iterator() {
        List<Path> result = new ArrayList<>();
        virtualFile.children(FileSearch.iterateAll(child -> result.add(new BridgePath(child, fileSystem))));

        return result.iterator();
    }

    @Override
    public void close() throws IOException {
        // Unused as no resources are allocated
    }
}
