/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.vfs.ftp;

import org.apache.ftpserver.ftplet.FileSystemView;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpFile;
import sirius.biz.storage.vfs.VFSRoot;
import sirius.biz.storage.vfs.VirtualFile;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Parts;

import java.util.Collection;
import java.util.function.Consumer;

/**
 * Providesa a bridge between the {@link sirius.biz.storage.vfs.VirtualFileSystem} and the FTP server.
 */
class BridgeFileSystemView implements FileSystemView {

    private VirtualFile root;
    private VirtualFile cwd;

    @Parts(VFSRoot.class)
    private static Collection<VFSRoot> roots;

    BridgeFileSystemView() {
        root = VirtualFile.createRootNode().withChildren(this::computeRoots);
        cwd = root;
    }

    private void computeRoots(VirtualFile parent, Consumer<VirtualFile> consumer) {
        for (VFSRoot childRoot : roots) {
            childRoot.collectRootFolders(parent, consumer);
        }
    }

    @Override
    public FtpFile getHomeDirectory() throws FtpException {
        return new BridgeFile(root);
    }

    @Override
    public FtpFile getWorkingDirectory() throws FtpException {
        return new BridgeFile(cwd);
    }

    @Override
    public boolean changeWorkingDirectory(String path) throws FtpException {
        BridgeFile file = resolve(path);
        if (file != null && file.getVirtualFile() != null && file.isDirectory()) {
            cwd = file.getVirtualFile();
            return true;
        }

        return false;
    }

    private BridgeFile resolve(String path) {
        VirtualFile result = cwd;
        if (Strings.isEmpty(path) || "/".equals(path)) {
            return new BridgeFile(result);
        }

        String[] pathElements = path.split("/");
        for (int i = 0; i < pathElements.length; i++) {
            String pathElement = pathElements[i];
            if (pathElement != null && pathElement.isEmpty()) {
                result = root;
            } else if ("..".equals(pathElement)) {
                if (result.getParent() == null) {
                    result = root;
                } else {
                    result = result.getParent();
                }
            } else if ("~".equals(pathElement)) {
                result = root;
            } else if (".".equals(pathElement)) {
                /* NOOP */
            } else {
                VirtualFile child = result.findChild(pathElement).orElse(null);
                if (child == null) {
                    if (i == pathElements.length - 1) {
                        return new BridgeFile(result, pathElement);
                    } else {
                        return null;
                    }
                } else {
                    result = child;
                }
            }
        }

        return new BridgeFile(result);
    }

    @Override
    public FtpFile getFile(String path) throws FtpException {
        BridgeFile file = resolve(path);
        if (file == null) {
            throw new FtpException("Invalid path");
        }

        return file;
    }

    @Override
    public boolean isRandomAccessible() throws FtpException {
        return false;
    }

    @Override
    public void dispose() {

    }
}
