/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.uplink.fs;

import sirius.biz.storage.layer1.FileHandle;
import sirius.biz.storage.layer3.FileSearch;
import sirius.biz.storage.layer3.MutableVirtualFile;
import sirius.biz.storage.layer3.VirtualFile;
import sirius.biz.storage.layer3.uplink.ConfigBasedUplink;
import sirius.biz.storage.layer3.uplink.UplinkFactory;
import sirius.biz.storage.util.StorageUtils;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Function;

/**
 * Provides an uplink which maps into a given directory in the file system.
 */
public class LocalDirectoryUplink extends ConfigBasedUplink {

    /**
     * Specifies the base path on the remote server to map to "/".
     */
    public static final String CONFIG_BASE_PATH = "basePath";

    /**
     * Creates a new uplink for config sections which use "fs" as type.
     */
    @Register
    public static class Factory implements UplinkFactory {

        @Override
        public ConfigBasedUplink make(String id, Function<String, Value> config) {
            return new LocalDirectoryUplink(id, config, new File(config.apply(CONFIG_BASE_PATH).asString()));
        }

        @Nonnull
        @Override
        public String getName() {
            return "fs";
        }
    }

    private static final Comparator<File> SORT_BY_DIRECTORY = Comparator.comparing(file -> file.isDirectory() ? 0 : 1);
    private static final Comparator<File> SORT_BY_NAME = Comparator.comparing(file -> file.getName().toLowerCase());

    private final File root;

    protected LocalDirectoryUplink(String id, Function<String, Value> extension, File root) {
        super(id, extension);
        this.root = root;
        if (!root.exists() || !root.isDirectory()) {
            throw new IllegalArgumentException(Strings.apply("The given baseDir '%s' isn't an existing directory!",
                                                             root.getAbsolutePath()));
        }
    }

    @Override
    @Nullable
    protected VirtualFile findChildInDirectory(VirtualFile parent, String name) {
        try {
            File parentFile = parent.tryAs(File.class)
                                    .orElseThrow(() -> new IllegalArgumentException(Strings.apply(
                                            "Invalid parent: %s! Expecte a File!",
                                            parent)));
            File child = new File(parentFile, name);
            return wrapFile(parent, child);
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
        MutableVirtualFile result = createVirtualFile(parent, fileToWrap.getName());

        result.withExistsFlagSupplier(this::existsFlagSupplier)
              .withDirectoryFlagSupplier(this::isDirectoryFlagSupplier)
              .withSizeSupplier(this::sizeSupplier)
              .withLastModifiedSupplier(this::lastModifiedSupplier)
              .withDeleteHandler(this::deleteHandler)
              .withCanProvideInputStream(this::isExistingFile)
              .withInputStreamSupplier(this::inputStreamSupplier)
              .withCanProvideFileHandle(this::isExistingFile)
              .withFileHandleSupplier(this::fileHandleSupplier)
              .withOutputStreamSupplier(this::outputStreamSupplier)
              .withRenameHandler(this::renameHandler)
              .withCreateDirectoryHandler(this::createDirectoryHandler)
              .withCanFastMoveHandler(this::canFastMoveHandler)
              .withFastMoveHandler(this::fastMoveHandler);

        result.attach(fileToWrap);
        result.attach(this);

        return result;
    }

    private boolean fastMoveHandler(VirtualFile file, VirtualFile newParent) {
        try {
            Files.move(file.as(File.class).toPath(), new File(newParent.as(File.class), file.name()).toPath());
            return true;
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage("Layer 3/FS: Cannot move %s to %s: %s (%s)", file, name)
                            .handle();
        }
    }

    private boolean canFastMoveHandler(VirtualFile file, VirtualFile newParent) {
        return !readonly
               && this.equals(file.tryAs(LocalDirectoryUplink.class).orElse(null))
               && this.equals(newParent.tryAs(LocalDirectoryUplink.class).orElse(null));
    }

    private boolean renameHandler(VirtualFile file, String name) {
        try {
            File unwrappedFile = file.as(File.class);
            return unwrappedFile.renameTo(new File(unwrappedFile.getParentFile(), name));
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage("Layer 3/FS: Cannot rename %s to %s: %s (%s)", file, name)
                            .handle();
        }
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
            if (file.isDirectory()) {
                sirius.kernel.commons.Files.delete(file.as(File.class).toPath());
            } else {
                sirius.kernel.commons.Files.delete(file.as(File.class));
            }
            return true;
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

    private boolean isExistingFile(VirtualFile file) {
        return existsFlagSupplier(file) && !isDirectoryFlagSupplier(file);
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

    private FileHandle fileHandleSupplier(VirtualFile file) {
        try {
            return FileHandle.permanentFileHandle(file.as(File.class));
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage("Layer 3/FS: Cannot create a file handle of %s - %s (%s)", file)
                            .handle();
        }
    }

    private OutputStream outputStreamSupplier(VirtualFile file) {
        try {
            return new FileOutputStream(file.as(File.class));
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
            File[] children = parentFile.listFiles();
            if (children == null) {
                return;
            }
            Arrays.sort(children, SORT_BY_DIRECTORY.thenComparing(SORT_BY_NAME));
            for (File file : children) {
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
        MutableVirtualFile mutableVirtualFile = MutableVirtualFile.checkedCreate(parent, getDirectoryName());
        mutableVirtualFile.attach(root);
        return mutableVirtualFile;
    }
}
