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

public class BridgeDirectoryStream implements DirectoryStream<Path> {

    private VirtualFile virtualFile;
    private BridgeFileSystem fs;

    public BridgeDirectoryStream(VirtualFile virtualFile, BridgeFileSystem fs) {
        this.virtualFile = virtualFile;
        this.fs = fs;
    }

    @Override
    public Iterator<Path> iterator() {
        List<Path> result = new ArrayList<>();
        virtualFile.children(FileSearch.iterateAll(child -> result.add(new BridgePath(child, fs))));

        return result.iterator();
    }

    @Override
    public void close() throws IOException {
        // Unused as no resources are allocated
    }
}
