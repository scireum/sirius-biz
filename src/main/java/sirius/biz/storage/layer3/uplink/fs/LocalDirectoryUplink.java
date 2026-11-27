/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
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
import sirius.kernel.health.HandledException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Function;

/**
 * Provides an uplink which maps into a given directory in the file system.
 * <p>
 * The uplink is confined to its base directory. This also applies to symbolic links: a link which resides within
 * the base directory but points out of it is neither listed nor followed. To provide another directory, mount it as
 * an uplink of its own. The base directory itself may be given via a symbolic link, which is resolved once when the
 * uplink is created.
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

    /**
     * Contains the base directory with all symbolic links and relative segments being resolved, which is what
     * every accessed file is {@link #isWithinRoot(File) compared against}.
     */
    private final Path canonicalRoot;

    protected LocalDirectoryUplink(String id, Function<String, Value> extension, File root) {
        super(id, extension);
        this.root = root;
        if (!root.exists() || !root.isDirectory()) {
            throw new IllegalArgumentException(Strings.apply("The given baseDir '%s' isn't an existing directory!",
                                                             root.getAbsolutePath()));
        }

        try {
            this.canonicalRoot = root.getCanonicalFile().toPath();
        } catch (IOException exception) {
            throw new IllegalArgumentException(Strings.apply("The given baseDir '%s' cannot be resolved: %s",
                                                             root.getAbsolutePath(),
                                                             exception.getMessage()), exception);
        }
    }

    /**
     * Determines the file with the given name in the given directory, and ensures that it resides within the base
     * directory of this uplink.
     *
     * @param directory the directory which contains the file
     * @param name      the name of the file
     * @return the file with the given name
     * @throws IOException      if the file system fails to resolve the file
     * @throws HandledException if the file resides outside the base directory
     */
    private File resolveWithinRoot(File directory, String name) throws IOException {
        File file = new File(directory, name);
        if (!isWithinRoot(file)) {
            throw outsideOfRoot(name);
        }

        return file;
    }

    /**
     * Determines if the given file resides within the base directory of this uplink.
     * <p>
     * This is a second line of defense behind the checks of {@link VirtualFile}, which keep relative references
     * like <tt>..</tt> from reaching an uplink in the first place. It compares the actual location of the file,
     * and thus deliberately also refuses a symbolic link which resides within the base directory but points out of
     * it. A symbolic link without an existing target is refused as well, as its target cannot be verified and
     * writing to the link would create it.
     *
     * @param file the file to check
     * @return <tt>true</tt> if the file is the base directory or resides within it, <tt>false</tt> otherwise
     * @throws IOException if the file system fails to resolve the file
     */
    private boolean isWithinRoot(File file) throws IOException {
        Path path = file.toPath();
        if (Files.isSymbolicLink(path)) {
            return Files.exists(path) && path.toRealPath().startsWith(canonicalRoot);
        }

        return file.getCanonicalFile().toPath().startsWith(canonicalRoot);
    }

    /**
     * Provides the file represented by the given virtual file, once it has been verified to still reside within the
     * base directory of this uplink.
     * <p>
     * A file is verified when it is resolved. As the file system may change in the meantime, e.g. by a directory
     * being replaced with a symbolic link, this is repeated right before its contents are accessed or changed.
     *
     * @param file the virtual file to unwrap
     * @return the underlying file
     * @throws IOException      if the file system fails to resolve the file
     * @throws HandledException if the file resides outside the base directory
     */
    private File unwrapWithinRoot(VirtualFile file) throws IOException {
        File unwrappedFile = file.as(File.class);
        if (!isWithinRoot(unwrappedFile)) {
            throw outsideOfRoot(file.name());
        }

        return unwrappedFile;
    }

    /**
     * Creates the exception which reports that a file resides outside the base directory.
     * <p>
     * The exception is deliberately not logged, as the refusal is the intended outcome and can be provoked at will.
     *
     * @param name the name of the refused file
     * @return the exception to throw
     */
    private HandledException outsideOfRoot(String name) {
        return Exceptions.createHandled()
                         .withSystemErrorMessage("Layer 3/FS: '%s' resides outside of the base directory.", name)
                         .handle();
    }

    @Override
    @Nullable
    protected VirtualFile findChildInDirectory(VirtualFile parent, String name) {
        try {
            File parentFile = parent.tryAs(File.class)
                                    .orElseThrow(() -> new IllegalArgumentException(Strings.apply(
                                            "Invalid parent: %s! Expected a File!",
                                            parent)));
            return wrapFile(parent, resolveWithinRoot(parentFile, name));
        } catch (HandledException exception) {
            throw exception;
        } catch (Exception exception) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(exception)
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
              .withFastMoveHandler(this::fastMoveHandler)
              .withReadOnlyFlagSupplier(this::isReadOnlySupplier)
              .withReadOnlyHandler(this::setWritableHandler);

        result.attach(fileToWrap);
        result.attach(this);

        return result;
    }

    private boolean fastMoveHandler(VirtualFile file, VirtualFile newParent) {
        try {
            Files.move(unwrapWithinRoot(file).toPath(),
                       resolveWithinRoot(unwrapWithinRoot(newParent), file.name()).toPath());
            return true;
        } catch (Exception exception) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(exception)
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
            File unwrappedFile = unwrapWithinRoot(file);
            return unwrappedFile.renameTo(resolveWithinRoot(unwrappedFile.getParentFile(), name));
        } catch (Exception exception) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(exception)
                            .withSystemErrorMessage("Layer 3/FS: Cannot rename %s to %s: %s (%s)", file, name)
                            .handle();
        }
    }

    private boolean isReadOnlySupplier(VirtualFile file) {
        try {
            File unwrappedFile = file.as(File.class);
            if (unwrappedFile.exists()) {
                return !Files.isWritable(unwrappedFile.toPath());
            } else {
                return unwrappedFile.getParentFile() == null || !Files.isWritable(unwrappedFile.getParentFile()
                                                                                               .toPath());
            }
        } catch (Exception exception) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(exception)
                            .withSystemErrorMessage("Layer 3/FS: Cannot determine if %s is read-only: %s (%s)", file)
                            .handle();
        }
    }

    private boolean setWritableHandler(VirtualFile file, boolean readOnly) {
        try {
            return unwrapWithinRoot(file).setWritable(!readOnly);
        } catch (Exception exception) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(exception)
                            .withSystemErrorMessage("Layer 3/FS: Cannot set %s writable to %s: %s (%s)",
                                                    file,
                                                    !readOnly)
                            .handle();
        }
    }

    private boolean createDirectoryHandler(VirtualFile file) {
        try {
            return unwrapWithinRoot(file).mkdirs();
        } catch (Exception exception) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(exception)
                            .withSystemErrorMessage("Layer 3/FS: Cannot create %s as directory: %s (%s)", file)
                            .handle();
        }
    }

    private boolean existsFlagSupplier(VirtualFile file) {
        try {
            return file.as(File.class).exists();
        } catch (Exception exception) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(exception)
                            .withSystemErrorMessage("Layer 3/FS: Cannot determine if %s exists: %s (%s)", file)
                            .handle();
        }
    }

    private boolean deleteHandler(VirtualFile file) {
        try {
            File unwrappedFile = unwrapWithinRoot(file);
            if (file.isDirectory()) {
                sirius.kernel.commons.Files.delete(unwrappedFile.toPath());
            } else {
                sirius.kernel.commons.Files.delete(unwrappedFile);
            }
            return true;
        } catch (Exception exception) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(exception)
                            .withSystemErrorMessage("Layer 3/FS: Cannot delete %s: %s (%s)", file)
                            .handle();
        }
    }

    private boolean isDirectoryFlagSupplier(VirtualFile file) {
        try {
            return file.as(File.class).isDirectory();
        } catch (Exception exception) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(exception)
                            .withSystemErrorMessage("Layer 3/FS: Cannot determine if %s is a directory: %s (%s)", file)
                            .handle();
        }
    }

    private long sizeSupplier(VirtualFile file) {
        try {
            return file.as(File.class).length();
        } catch (Exception exception) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(exception)
                            .withSystemErrorMessage("Layer 3/FS: Cannot determine the size of %s - %s (%s)", file)
                            .handle();
        }
    }

    private long lastModifiedSupplier(VirtualFile file) {
        try {
            return file.as(File.class).lastModified();
        } catch (Exception exception) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(exception)
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
            return new FileInputStream(unwrapWithinRoot(file));
        } catch (Exception exception) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(exception)
                            .withSystemErrorMessage("Layer 3/FS: Cannot open input stream of %s - %s (%s)", file)
                            .handle();
        }
    }

    private FileHandle fileHandleSupplier(VirtualFile file) {
        try {
            return FileHandle.permanentFileHandle(unwrapWithinRoot(file));
        } catch (Exception exception) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(exception)
                            .withSystemErrorMessage("Layer 3/FS: Cannot create a file handle of %s - %s (%s)", file)
                            .handle();
        }
    }

    private OutputStream outputStreamSupplier(VirtualFile file) {
        try {
            return new FileOutputStream(unwrapWithinRoot(file));
        } catch (Exception exception) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(exception)
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
            if (!isWithinRoot(parentFile)) {
                throw outsideOfRoot(parent.name());
            }

            File[] children = parentFile.listFiles();
            if (children == null) {
                return;
            }
            Arrays.sort(children, SORT_BY_DIRECTORY.thenComparing(SORT_BY_NAME));
            for (File file : children) {
                // The parent has just been verified, so that only a link can point elsewhere
                if (Files.isSymbolicLink(file.toPath()) && !isWithinRoot(file)) {
                    continue;
                }

                if (!search.processResult(wrapFile(parent, file))) {
                    return;
                }
            }
        } catch (HandledException exception) {
            throw exception;
        } catch (Exception exception) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(exception)
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
