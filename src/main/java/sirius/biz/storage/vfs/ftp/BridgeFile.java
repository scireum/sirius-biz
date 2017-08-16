/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.vfs.ftp;

import com.google.common.io.ByteStreams;
import org.apache.ftpserver.ftplet.FtpFile;
import sirius.biz.storage.vfs.VirtualFile;
import sirius.biz.storage.vfs.VirtualFileSystem;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides a bridge between {@link VirtualFile} and the FTP server.
 */
class BridgeFile implements FtpFile {

    private static final byte[] EMPTY_BUFFER = new byte[0];
    private VirtualFile file;
    private VirtualFile parent;
    private String childName;

    BridgeFile(VirtualFile file) {
        this.file = file;
    }

    BridgeFile(VirtualFile parent, String childName) {
        this.parent = parent;
        this.childName = childName;
    }

    @Override
    public String getAbsolutePath() {
        if (file != null) {
            return file.getPath();
        }
        return parent.getPath() + "/" + childName;
    }

    @Override
    public String getName() {
        if (file != null) {
            return file.getName();
        } else {
            return childName;
        }
    }

    @Override
    public boolean isHidden() {
        return false;
    }

    @Override
    public boolean isDirectory() {
        return file != null && file.isDirectory();
    }

    @Override
    public boolean isFile() {
        return file != null && !file.isDirectory();
    }

    @Override
    public boolean doesExist() {
        return file != null;
    }

    @Override
    public boolean isReadable() {
        return true;
    }

    @Override
    public boolean isRemovable() {
        return true;
    }

    @Override
    public boolean isWritable() {
        return true;
    }

    @Override
    public String getOwnerName() {
        return "";
    }

    @Override
    public String getGroupName() {
        return "";
    }

    @Override
    public int getLinkCount() {
        return isDirectory() ? 3 : 1;
    }

    @Override
    public long getLastModified() {
        return file != null ? file.getLastModified() : 0;
    }

    @Override
    public boolean setLastModified(long l) {
        return false;
    }

    @Override
    public long getSize() {
        return isFile() ? file.getSize() : 0;
    }

    @Override
    public Object getPhysicalFile() {
        return null;
    }

    @Override
    public boolean mkdir() {
        if (file != null) {
            return file.isDirectory();
        } else {
            return parent.createChildDirectory(childName);
        }
    }

    @Override
    public boolean delete() {
        if (file != null) {
            return file.delete();
        }

        return true;
    }

    @Override
    public boolean move(FtpFile destination) {
        try {
            if (file == null) {
                return false;
            }

            if (destination.isWritable() && this.isReadable()) {
                try (OutputStream out = destination.createOutputStream(0L); InputStream in = createInputStream(0L)) {
                    ByteStreams.copy(in, out);
                }
            }
            return this.delete();
        } catch (IOException e) {
            VirtualFileSystem.LOG.WARN(e);
            return false;
        }
    }

    @Override
    public List<? extends FtpFile> listFiles() {
        List<FtpFile> result = new ArrayList<>();
        if (file != null) {
            file.enumerateChildren(child -> result.add(new BridgeFile(child)));
        }

        return result;
    }

    @Override
    public OutputStream createOutputStream(long offset) throws IOException {
        if (file != null) {
            return file.createOutputStream();
        }

        return parent.createChildFile(childName);
    }

    @Override
    public InputStream createInputStream(long offset) throws IOException {
        if (file != null) {
            return file.createInputStream();
        }

        return new ByteArrayInputStream(EMPTY_BUFFER);
    }

    public VirtualFile getVirtualFile() {
        return file;
    }

    @Override
    public String toString() {
        if (file != null) {
            return file.getPath();
        }

        return parent.getPath() + "/" + childName;
    }
}
