/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.uplink;

import sirius.biz.storage.layer3.FileSearch;
import sirius.biz.storage.layer3.MutableVirtualFile;
import sirius.biz.storage.layer3.VirtualFile;
import sirius.biz.storage.util.StorageUtils;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.settings.Extension;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Optional;

/**
 * Provides an uplink which maps into a given directory in the file system.
 */
public class LocalDirectoryRoot extends ConfigBasedUplink {

    /**
     * Creates a new uplink for config sections which use "fs" as type.
     */
    @Register
    public static class Factory implements ConfigBasedUplinkFactory {

        @Override
        public ConfigBasedUplink make(Extension config) {
            return new LocalDirectoryRoot(config, new File(config.get("basePath").asString()));
        }

        @Nonnull
        @Override
        public String getName() {
            return "fs";
        }
    }

    private File root;

    protected LocalDirectoryRoot(Extension extension, File root) {
        super(extension);
        this.root = root;
        if (!root.exists() || !root.isDirectory()) {
            throw new IllegalArgumentException(Strings.apply("The given baseDir '%s' isn't an existing directory!",
                                                             root.getAbsolutePath()));
        }
    }

    @Override
    protected Optional<VirtualFile> findChildInDirectory(VirtualFile parent, String name) {
        try {
            File parentFile = parent.tryAs(File.class)
                                    .orElseThrow(() -> new IllegalArgumentException(Strings.apply(
                                            "Invalid parent: %s! Expecte a File!",
                                            parent)));
            File child = new File(parentFile, name);
            return Optional.of(wrapFile(parent, child));
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage("Layer 3/FS: Cannot resolve file: %s in parent: %s - %s (%s)",
                                                    name,
                                                    parent)
                            .handle();
        }
    }

    private MutableVirtualFile wrapFile(VirtualFile parent, File fileToWrap) {
        MutableVirtualFile result =
                new MutableVirtualFile(parent, fileToWrap.getName()).withExistsFlagSupplier(this::existsFlagSupplier)
                                                                    .withDirectoryFlagSupplier(this::isDirectoryFlagSupplier)
                                                                    .withSizeSupplier(this::sizeSupplier)
                                                                    .withLastModifiedSupplier(this::lastModifiedSupplier)
                                                                    .withCanDeleteHandler(this::canDeleteHandler)
                                                                    .withDeleteHandler(this::deleteHandler)
                                                                    .withCanCreateChildren(this::canCreateChildrenHandler)
                                                                    .withCanCreateDirectoryHandler(this::canCreateDirectoryHandler)
                                                                    .withCreateDirectoryHandler(this::createDirectoryHandler)
                                                                    .withCanProvideInputStream(this::canProvideInputStreamHandler)
                                                                    .withInputStreamSupplier(this::inputStreamSupplier)
                                                                    .withCanProvideOutputStream(this::canCreateOutputStream)
                                                                    .withOutputStreamSupplier(this::outputStreamSupplier)
                                                                    .withCanRenameHandler(this::canRenameHandler)
                                                                    .withRenameHandler(this::renameHandler)
                                                                    .withCanMoveHandler(this::canMoveHandler)
                                                                    .withMoveHandler(this::moveHandler)
                                                                    .withChildren(innerChildProvider);
        result.attach(fileToWrap);
        return result;
    }

    private boolean moveHandler(VirtualFile file, VirtualFile newParent) {
        try {
            File parent = newParent.tryAs(File.class).orElse(null);
            if (parent == null) {
                return false;
            }

            Files.move(file.as(File.class).toPath(), new File(parent, file.name()).toPath());
            return true;
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage("Layer 3/FS: Cannot move %s to %s: %s (%s)", file, name)
                            .handle();
        }
    }

    private boolean canMoveHandler(VirtualFile file) {
        return !readonly && file.exists();
    }

    private boolean renameHandler(VirtualFile file, String name) {
        try {
            return file.as(File.class).renameTo(new File(name));
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage("Layer 3/FS: Cannot rename %s to %s: %s (%s)", file, name)
                            .handle();
        }
    }

    private boolean canRenameHandler(VirtualFile file) {
        return !readonly && file.exists();
    }

    private boolean canCreateOutputStream(VirtualFile file) {
        return !readonly && !file.isDirectory();
    }

    private boolean canProvideInputStreamHandler(VirtualFile file) {
        return file.exists() && !file.isDirectory();
    }

    private boolean createDirectoryHandler(VirtualFile file) {
        try {
            return file.as(File.class).mkdirs();
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage("Layer 3/FS: Cannot create %s as directory: %s (%s)", file)
                            .handle();
        }
    }

    private boolean canCreateDirectoryHandler(VirtualFile file) {
        return !readonly && (!file.exists() || file.isDirectory());
    }

    private boolean canCreateChildrenHandler(VirtualFile file) {
        return file.exists() && file.isDirectory() && !readonly;
    }

    private boolean canDeleteHandler(VirtualFile file) {
        return !readonly && file.exists();
    }

    private boolean existsFlagSupplier(VirtualFile file) {
        try {
            return file.as(File.class).exists();
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage("Layer 3/FS: Cannot determine if %s exists: %s (%s)", file)
                            .handle();
        }
    }

    private boolean deleteHandler(VirtualFile file) {
        try {
            if (readonly) {
                return false;
            } else {
                sirius.kernel.commons.Files.delete(file.as(File.class));
                return true;
            }
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage("Layer 3/FS: Cannot delete %s: %s (%s)", file)
                            .handle();
        }
    }

    private boolean isDirectoryFlagSupplier(VirtualFile file) {
        try {
            return file.as(File.class).isDirectory();
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage("Layer 3/FS: Cannot determine if %s is a directory: %s (%s)", file)
                            .handle();
        }
    }

    private long sizeSupplier(VirtualFile file) {
        try {
            return file.as(File.class).length();
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage("Layer 3/FS: Cannot determine the size of %s - %s (%s)", file)
                            .handle();
        }
    }

    private long lastModifiedSupplier(VirtualFile file) {
        try {
            return file.as(File.class).lastModified();
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage("Layer 3/FS: Cannot determine the last modified of %s - %s (%s)",
                                                    file)
                            .handle();
        }
    }

    private InputStream inputStreamSupplier(VirtualFile file) {
        try {
            return new FileInputStream(file.as(File.class));
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage("Layer 3/FS: Cannot open input stream of %s - %s (%s)", file)
                            .handle();
        }
    }

    private OutputStream outputStreamSupplier(VirtualFile file) {
        try {
            if (!readonly) {
                return new FileOutputStream(file.as(File.class));
            } else {
                return null;
            }
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage("Layer 3/FS: Cannot open output stream of %s - %s (%s)", file)
                            .handle();
        }
    }

    @Override
    protected void enumerateDirectoryChildren(VirtualFile parent, FileSearch search) {
        File parentFile = parent.tryAs(File.class)
                                .orElseThrow(() -> new IllegalArgumentException(Strings.apply(
                                        "Invalid parent: %s! Expected a File!",
                                        parent)));
        try {
            for (File file : parentFile.listFiles()) {
                if (!search.processResult(wrapFile(parent, file))) {
                    return;
                }
            }
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage("Layer 3/FS: Cannot iterate over children of %s - %s (%s)", parent)
                            .handle();
        }
    }

    @Override
    protected MutableVirtualFile createDirectoryFile(@Nonnull VirtualFile parent) {
        MutableVirtualFile mutableVirtualFile = new MutableVirtualFile(parent, getDirectoryName());
        mutableVirtualFile.attach(root);
        return mutableVirtualFile;
    }
}
