/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.uplink.cifs;

import jcifs.CIFSContext;
import jcifs.CIFSException;
import jcifs.config.BaseConfiguration;
import jcifs.context.BaseContext;
import jcifs.smb.NtlmPasswordAuthenticator;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import sirius.biz.storage.layer3.FileSearch;
import sirius.biz.storage.layer3.MutableVirtualFile;
import sirius.biz.storage.layer3.VirtualFile;
import sirius.biz.storage.layer3.uplink.ConfigBasedUplink;
import sirius.biz.storage.layer3.uplink.ConfigBasedUplinkFactory;
import sirius.biz.storage.util.StorageUtils;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.settings.Extension;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Provides an uplink which maps into a given CIFS mount point.
 */
public class CIFSUplink extends ConfigBasedUplink {

    /**
     * Creates a new uplink for config sections which use "cifs" as type.
     */
    @Register
    public static class Factory implements ConfigBasedUplinkFactory {

        @Override
        public ConfigBasedUplink make(Extension config) {
            String url = config.get("url").asString();
            String domain = config.get("domain").asString();
            String user = config.get("user").asString();
            String password = config.get("password").asString();

            try {
                if (Strings.isFilled(user)) {
                    if (Strings.isEmpty(domain)) {
                        throw new IllegalArgumentException("A user has been specified but no domain was given!");
                    }
                    CIFSContext context =
                            new BaseContext(new BaseConfiguration(true)).withCredentials(new NtlmPasswordAuthenticator(
                                    domain,
                                    user,
                                    password));
                    return new CIFSUplink(config, context, new SmbFile(url, context));
                } else {
                    CIFSContext context = new BaseContext(new BaseConfiguration(true));
                    return new CIFSUplink(config, context, new SmbFile(url, context));
                }
            } catch (CIFSException e) {
                throw new IllegalArgumentException(Strings.apply(
                        "An error occurred when talking to the CIFS file system: %s - %s",
                        url,
                        e.getMessage()));
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException(Strings.apply("An invalid url (%s) was given: %s",
                                                                 url,
                                                                 e.getMessage()));
            }
        }

        @Nonnull
        @Override
        public String getName() {
            return "cifs";
        }
    }

    private static final Comparator<SmbFile> SORT_BY_DIRECTORY = Comparator.comparing(file -> {
        try {
            return file.isDirectory() ? 0 : 1;
        } catch (SmbException e) {
            Exceptions.handle()
                      .to(StorageUtils.LOG)
                      .error(e)
                      .withSystemErrorMessage("Layer 3/CIFS: Cannot determine if the file: %s is a directory: %s (%s)",
                                              file.getName())
                      .handle();
            return 0;
        }
    });
    private static final Comparator<SmbFile> SORT_BY_NAME = Comparator.comparing(file -> file.getName().toLowerCase());

    protected SmbFile smbRoot;
    protected CIFSContext context;

    protected CIFSUplink(Extension config, CIFSContext context, SmbFile smbRoot) {
        super(config);
        this.context = context;
        this.smbRoot = smbRoot;
    }

    @Override
    @Nullable
    protected VirtualFile findChildInDirectory(VirtualFile parent, String name) {
        try {
            SmbFile parentFile = parent.tryAs(SmbFile.class)
                                       .orElseThrow(() -> new IllegalArgumentException(Strings.apply(
                                               "Invalid parent: %s! Expected a SmbFile!",
                                               parent)));
            SmbFile child = new SmbFile(parentFile, name);
            if (child.isDirectory()) {
                return wrapSmbFile(parent, new SmbFile(parentFile, name + "/"));
            }
            return wrapSmbFile(parent, child);
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage("Layer 3/CIFS: Cannot resolve file: %s in parent: %s - %s (%s)",
                                                    name,
                                                    parent)
                            .handle();
        }
    }

    private MutableVirtualFile wrapSmbFile(VirtualFile parent, SmbFile file) {
        String filename = file.getName();
        if (filename.endsWith("/")) {
            filename = filename.substring(0, filename.length() - 1);
        }

        MutableVirtualFile result = createVirtualFile(parent, filename);

        result.withExistsFlagSupplier(this::existsFlagSupplier)
              .withDirectoryFlagSupplier(this::isDirectoryFlagSupplier)
              .withSizeSupplier(this::sizeSupplier)
              .withLastModifiedSupplier(this::lastModifiedSupplier)
              .withDeleteHandler(this::deleteHandler)
              .withInputStreamSupplier(this::inputStreamSupplier)
              .withOutputStreamSupplier(this::outputStreamSupplier)
              .withRenameHandler(this::renameHandler)
              .withCreateDirectoryHandler(this::createDirectoryHandler)
              .withCanFastMoveHandler(this::canFastMoveHandler)
              .withFastMoveHandler(this::fastMoveHandler);

        result.attach(file);
        result.attach(this);
        return result;
    }

    private boolean fastMoveHandler(VirtualFile file, VirtualFile newParent) {
        try {
            file.as(SmbFile.class).renameTo(new SmbFile(newParent.as(SmbFile.class), name));
            return true;
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage("Layer 3/CIFS: Cannot move %s to %s: %s (%s)", file, name)
                            .handle();
        }
    }

    private boolean canFastMoveHandler(VirtualFile file, VirtualFile newParent) {
        return !readonly  && this.equals(file.tryAs(CIFSUplink.class).orElse(null)) && this.equals(
                newParent.tryAs(CIFSUplink.class).orElse(null));
    }

    private boolean renameHandler(VirtualFile file, String name) {
        try {
            file.as(SmbFile.class).renameTo(new SmbFile(file.parent().as(SmbFile.class), name));
            return true;
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage("Layer 3/CIFS: Cannot rename %s to %s: %s (%s)", file, name)
                            .handle();
        }
    }

    private boolean createDirectoryHandler(VirtualFile file) {
        try {
            file.as(SmbFile.class).mkdirs();
            return true;
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage("Layer 3/CIFS: Cannot create %s as directory: %s (%s)", file)
                            .handle();
        }
    }

    private boolean existsFlagSupplier(VirtualFile file) {
        try {
            return file.as(SmbFile.class).exists();
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage("Layer 3/CIFS: Cannot determine if %s exists: %s (%s)", file)
                            .handle();
        }
    }

    private InputStream inputStreamSupplier(VirtualFile file) {
        try {
            return file.as(SmbFile.class).getInputStream();
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage("Layer 3/CIFS: Cannot open InputStream for %s: %s (%s)", file)
                            .handle();
        }
    }

    private OutputStream outputStreamSupplier(VirtualFile file) {
        try {
            return file.as(SmbFile.class).getOutputStream();
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage("Layer 3/CIFS: Cannot open OutputStream for %s: %s (%s)", file)
                            .handle();
        }
    }

    private boolean deleteHandler(VirtualFile file) {
        try {
            file.as(SmbFile.class).delete();
            return true;
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage("Layer 3/CIFS: Cannot delete %s: %s (%s)", file)
                            .handle();
        }
    }

    private boolean isDirectoryFlagSupplier(VirtualFile file) {
        try {
            return file.as(SmbFile.class).isDirectory();
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage("Layer 3/CIFS: Cannot determine if %s is a directory: %s (%s)",
                                                    file)
                            .handle();
        }
    }

    private long sizeSupplier(VirtualFile file) {
        try {
            SmbFile smbFile = file.as(SmbFile.class);
            if (smbFile.isDirectory()) {
                return 0;
            }

            return smbFile.length();
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage("Layer 3/CIFS: Cannot determine the size of %s - %s (%s)", file)
                            .handle();
        }
    }

    private long lastModifiedSupplier(VirtualFile file) {
        try {
            return file.as(SmbFile.class).getLastModified();
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage("Layer 3/CIFS: Cannot determine last modified of %s - %s (%s)",
                                                    file)
                            .handle();
        }
    }

    @Override
    protected void enumerateDirectoryChildren(@Nonnull VirtualFile parent, FileSearch search) {
        SmbFile parentFile = parent.tryAs(SmbFile.class)
                                   .orElseThrow(() -> new IllegalArgumentException(Strings.apply(
                                           "Invalid parent: %s! Expecte a SmbFile!",
                                           parent)));
        try {
            SmbFile[] children = parentFile.listFiles();
            Arrays.sort(children, SORT_BY_DIRECTORY.thenComparing(SORT_BY_NAME));
            for (SmbFile smbFile : children) {
                if (!search.processResult(wrapSmbFile(parent, smbFile))) {
                    return;
                }
            }
        } catch (SmbException e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage("Layer 3/CIFS: Cannot iterate over children of %s - %s (%s)",
                                                    parent)
                            .handle();
        }
    }

    @Override
    protected MutableVirtualFile createDirectoryFile(@Nonnull VirtualFile parent) {
        MutableVirtualFile mutableVirtualFile = MutableVirtualFile.checkedCreate(parent, getDirectoryName());
        mutableVirtualFile.attach(smbRoot);
        return mutableVirtualFile;
    }
}
