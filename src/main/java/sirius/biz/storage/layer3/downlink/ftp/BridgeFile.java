/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.downlink.ftp;

import com.google.common.io.ByteStreams;
import org.apache.ftpserver.ftplet.FtpFile;
import sirius.biz.storage.layer3.FileSearch;
import sirius.biz.storage.layer3.Transfer;
import sirius.biz.storage.layer3.VirtualFile;
import sirius.biz.storage.util.StorageUtils;
import sirius.kernel.commons.Strings;
import sirius.kernel.health.Exceptions;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
            return file.path();
        }
        return parent.path() + "/" + childName;
    }

    @Override
    public String getName() {
        if (file != null) {
            return file.name();
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
        return file != null && file.exists();
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
        return file != null ? file.lastModified() : 0;
    }

    @Override
    public boolean setLastModified(long l) {
        return false;
    }

    @Override
    public long getSize() {
        return isFile() ? file.size() : 0;
    }

    @Override
    public Object getPhysicalFile() {
        return null;
    }

    @Override
    public boolean mkdir() {
        if (doesExist()) {
            return file.isDirectory();
        } else {
            return parent.resolve(childName).tryCreateAsDirectory();
        }
    }

    @Override
    public boolean delete() {
        if (doesExist()) {
            return file.tryDelete();
        }

        return true;
    }

    @Override
    public boolean move(FtpFile destination) {
        if (destination instanceof BridgeFile other) {
            // Detect and optimize renames...
            if (Objects.equals(file.parent(), other.parent)) {
                file.rename(other.childName);
                return true;
            }

            // Detect and optimize moves into another directory if possible...
            if (other.file == null && Strings.areEqual(file.name(), other.childName)) {
                Transfer transfer = file.transferTo(other.parent);
                if (transfer.tryFastMove()) {
                    return true;
                }
            }
        }

        try {
            if (!doesExist()) {
                return false;
            }

            if (destination.isWritable() && this.isReadable()) {
                try (OutputStream out = destination.createOutputStream(0L); InputStream in = createInputStream(0L)) {
                    ByteStreams.copy(in, out);
                }
            }
            return this.delete();
        } catch (IOException e) {
            Exceptions.handle()
                      .to(StorageUtils.LOG)
                      .error(e)
                      .withSystemErrorMessage("Layer3/FTP: Cannot move file %s to %s - %s (%s)",
                                              getAbsolutePath(),
                                              destination.getAbsolutePath());
            return false;
        }
    }

    @Override
    public List<? extends FtpFile> listFiles() {
        List<FtpFile> result = new ArrayList<>();
        if (doesExist()) {
            file.children(FileSearch.iterateAll(child -> result.add(new BridgeFile(child))));
        }

        return result;
    }

    @Override
    public OutputStream createOutputStream(long offset) throws IOException {
        if (doesExist()) {
            return file.createOutputStream();
        }
        StorageUtils.LOG.FINE("Layer3/FTP:  Uploading file '%s' in '%s'", childName, parent);

        return parent.resolve(childName).createOutputStream();
    }

    @Override
    public InputStream createInputStream(long offset) throws IOException {
        if (doesExist()) {
            return file.createInputStream();
        }
        StorageUtils.LOG.FINE("Layer3/FTP:  Downloading file '%s' from '%s'", childName, parent);

        return new ByteArrayInputStream(EMPTY_BUFFER);
    }

    public VirtualFile getVirtualFile() {
        return file;
    }

    @Override
    public String toString() {
        return getAbsolutePath();
    }
}
