/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.vfs.ftp;

import org.apache.ftpserver.ftplet.FileSystemView;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpFile;
import sirius.biz.vfs.VFSRoot;
import sirius.biz.vfs.VirtualFile;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Parts;

import java.util.Collection;
import java.util.function.Consumer;

/**
 * Providesa a bridge between the {@link sirius.biz.vfs.VirtualFileSystem} and the FTP server.
 */
class BridgeFileSystemView implements FileSystemView {

    public static final String PATH_SEPARATOR = "/";
    private VirtualFile root;
    private VirtualFile cwd;

    @Parts(VFSRoot.class)
    private static Collection<VFSRoot> roots;

    BridgeFileSystemView() {
        root = VirtualFile.createRootNode().withChildren(this::computeRoots);
        cwd = root;
    }

    private void computeRoots(VirtualFile parent, Consumer<VirtualFile> fileCollector) {
        for (VFSRoot childRoot : roots) {
            childRoot.collectRootFolders(parent, fileCollector);
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
        if (Strings.isEmpty(path) || PATH_SEPARATOR.equals(path)) {
            return new BridgeFile(root);
        }

        String[] pathElements = path.split(PATH_SEPARATOR);
        for (int i = 0; i < pathElements.length; i++) {
            String pathElement = pathElements[i];
            VirtualFile child = resolveSpecialFiles(pathElement, result);
            if (child != null) {
                result = child;
            } else {
                child = result.findChild(pathElement).orElse(null);
                if (child != null) {
                    result = child;
                } else if (i == pathElements.length - 1) {
                    // The last path element is unknown - create a placeholder child and let the parent file decide
                    // if can create this...
                    return new BridgeFile(result, pathElement);
                } else {
                    // We cannot do this several layers deep, only the last path element may be new...
                    return null;
                }
            }
        }

        return new BridgeFile(result);
    }

    private VirtualFile resolveSpecialFiles(String pathElement, VirtualFile current) {
        if (pathElement != null && pathElement.isEmpty()) {
            return root;
        }

        if ("..".equals(pathElement)) {
            if (current.getParent() == null) {
                return root;
            } else {
                return current.getParent();
            }
        }

        if ("~".equals(pathElement)) {
            return root;
        }

        if (".".equals(pathElement)) {
            return current;
        }

        return null;
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
        // Nothing to release
    }
}
