/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3;

import io.netty.handler.codec.http.HttpResponseStatus;
import sirius.biz.storage.layer1.FileHandle;
import sirius.biz.storage.layer2.Blob;
import sirius.biz.storage.util.Attempt;
import sirius.biz.storage.util.StorageUtils;
import sirius.kernel.commons.Files;
import sirius.kernel.commons.Streams;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.transformers.Composable;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.kernel.nls.NLS;
import sirius.web.http.MimeHelper;
import sirius.web.http.Response;
import sirius.web.http.WebContext;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

/**
 * Represents a file or directory in the {@link VirtualFileSystem}.
 * <p>
 * This is the work horse of the VFS. The main purpose of this class is to provide a uniform API and to take care of
 * error and exception handling. Depending on the capabilities of the underlying provider this class (or actually
 * its mutable counterpart {@link MutableVirtualFile}) can be supplied with the appropriate callbacks to handle the
 * requested functionality.
 * <p>
 * As in most cases nearly all of the functions will be delegated to other classes, this class uses callbacks instead of
 * a proper class hierarchy.
 */
public abstract class VirtualFile extends Composable implements Comparable<VirtualFile> {

    private static final String HANDLER_OUTPUT_STREAM_SUPPLIER = "outputStreamSupplier";
    private static final String HANDLER_CONSUME_FILE_HANDLER = "consumeFileHandler";

    protected String name;
    protected String description;
    protected VirtualFile parent;
    protected ChildProvider childProvider;
    protected ToLongFunction<VirtualFile> lastModifiedSupplier;
    protected ToLongFunction<VirtualFile> sizeSupplier;
    protected Predicate<VirtualFile> directoryFlagSupplier;
    protected Predicate<VirtualFile> existsFlagSupplier;
    protected Predicate<VirtualFile> canCreateChildrenHandler;
    protected Predicate<VirtualFile> canCreateDirectoryHandler;
    protected Predicate<VirtualFile> createDirectoryHandler;
    protected Predicate<VirtualFile> canDeleteHandler;
    protected Predicate<VirtualFile> deleteHandler;
    protected Predicate<VirtualFile> canProvideOutputStream;
    protected Function<VirtualFile, OutputStream> outputStreamSupplier;
    protected Predicate<VirtualFile> canConsumeStream;
    protected BiConsumer<VirtualFile, Tuple<InputStream, Long>> consumeStreamHandler;
    protected Predicate<VirtualFile> canConsumeFile;
    protected BiConsumer<VirtualFile, File> consumeFileHandler;
    protected Predicate<VirtualFile> canProvideInputStream;
    protected Function<VirtualFile, InputStream> inputStreamSupplier;
    protected Predicate<VirtualFile> canProvideFileHandle;
    protected Function<VirtualFile, FileHandle> fileHandleSupplier;
    protected BiConsumer<VirtualFile, Response> tunnelHandler = VirtualFile::defaultTunnelHandler;
    protected BiPredicate<VirtualFile, VirtualFile> canFastMoveHandler;
    protected BiPredicate<VirtualFile, VirtualFile> fastMoveHandler;
    protected Predicate<VirtualFile> canRenameHandler;
    protected BiPredicate<VirtualFile, String> renameHandler;

    @Part
    private static StorageUtils utils;

    /**
     * Internal constructor to create the "/" directory.
     */
    protected VirtualFile() {
        this.name = null;
    }

    /**
     * Creates a new file with the given name.
     *
     * @param parent the parent of the file
     * @param name   the name of the file
     */
    protected VirtualFile(@Nonnull VirtualFile parent, @Nonnull String name) {
        this.parent = parent;
        this.name = name;
    }

    /**
     * Determines if the file represents a directory.
     *
     * @return <tt>true</tt> if the file represents a directory, <tt>false</tt> otherwise
     */
    public boolean isDirectory() {
        try {
            if (directoryFlagSupplier != null) {
                return directoryFlagSupplier.test(this);
            }
            return childProvider != null;
        } catch (Exception e) {
            throw handleErrorInCallback(e, "directoryFlagSupplier");
        }
    }

    /**
     * Determines if the file represents a file.
     *
     * @return <tt>true</tt> if the file represents a file, <tt>false</tt> otherwise
     */
    public boolean isFile() {
        return !isDirectory();
    }

    private HandledException handleErrorInCallback(Exception e, String callback) {
        return Exceptions.handle()
                         .to(StorageUtils.LOG)
                         .error(e)
                         .withSystemErrorMessage("Layer 3/VFS: An error occurred in the '%s' of '%s': %s (%s)",
                                                 callback,
                                                 path())
                         .handle();
    }

    /**
     * Returns the parent file of this file.
     *
     * @return the parent of this file
     */
    public VirtualFile parent() {
        return parent;
    }

    /**
     * Returns the absolute path of this file within the virtual file system.
     *
     * @return the absolute path of this file
     */
    public String path() {
        if (parent != null) {
            if (parent.name == null) {
                return "/" + name;
            } else {
                return parent.path() + "/" + name;
            }
        }

        return name();
    }

    /**
     * Returns the relative path to the given root parent.
     * <p>
     * If this would be <tt>/foo/bar/baz</tt> and the given root parent is <tt>/foo</tt> then this would
     * return <tt>bar/baz</tt>. Therefore this is the inverse of {@link #resolve(String)}.
     *
     * @param rootParent one of the parent directories of <tt>this</tt>
     * @return the relative path from the given root parent to <tt>this</tt>
     * @throws IllegalArgumentException if the given root parent isn't part of the path of <tt>this</tt>
     */
    public String relativePath(VirtualFile rootParent) {
        return relativePathList(rootParent).stream().map(VirtualFile::name).collect(Collectors.joining("/"));
    }

    /**
     * Returns the path as a list of virtual files from the given root parent to this.
     *
     * @param rootParent one of the parent directories of <tt>this</tt>
     * @return the path (from the given root directory to this file) as list
     * @throws IllegalArgumentException if the given root parent isn't part of the path of <tt>this</tt>
     */
    public List<VirtualFile> relativePathList(VirtualFile rootParent) {
        List<VirtualFile> result = new ArrayList<>();
        VirtualFile current = this;
        while (current != null && !Objects.equals(current, rootParent)) {
            result.add(0, current);
            current = current.parent();
        }

        if (current == null) {
            throw new IllegalArgumentException(Strings.apply("%s is not a parent of %s", rootParent, this));
        }

        return result;
    }

    /**
     * Returns the path a list of virtual files.
     *
     * @return the path (from the root directory to this file) as list
     */
    public List<VirtualFile> pathList() {
        List<VirtualFile> result = new ArrayList<>();
        VirtualFile current = this;
        while (current != null) {
            result.add(0, current);
            current = current.parent();
        }
        return result;
    }

    /**
     * Returns the name of this file.
     *
     * @return the name of this file
     */
    public String name() {
        return name == null ? "/" : name;
    }

    /**
     * Returns the file extension of the {@link #name()}.
     *
     * @return the file extension or <tt>null</tt> if there is none
     */
    @Nullable
    public String fileExtension() {
        return Files.getFileExtension(name());
    }

    /**
     * Returns a short description of the file.
     *
     * @return the description of the file
     */
    @Nullable
    public String description() {
        return NLS.smartGet(description);
    }

    /**
     * Returns the last modification timestamp.
     *
     * @return the last modification as in {@link File#lastModified()}
     */
    public long lastModified() {
        try {
            return lastModifiedSupplier == null ? 0 : lastModifiedSupplier.applyAsLong(this);
        } catch (Exception e) {
            throw handleErrorInCallback(e, "lastModifiedSupplier");
        }
    }

    /**
     * Returns the last modification timestamp as {@link LocalDateTime}.
     *
     * @return the last modification timestamp
     */
    public LocalDateTime lastModifiedDate() {
        try {
            long lastModified = lastModified();
            if (lastModified == 0) {
                return null;
            } else {
                return Instant.ofEpochMilli(lastModified).atOffset(ZoneOffset.UTC).toLocalDateTime();
            }
        } catch (Exception e) {
            throw handleErrorInCallback(e, "lastModifiedSupplier");
        }
    }

    /**
     * Returns the size of this file.
     *
     * @return the size in bytes
     */
    public long size() {
        try {
            return sizeSupplier == null ? 0 : sizeSupplier.applyAsLong(this);
        } catch (Exception e) {
            throw handleErrorInCallback(e, "sizeSupplier");
        }
    }

    /**
     * Determines if this file can (probably) be deleted.
     *
     * @return <tt>true</tt> if this file can be deleted or <tt>false</tt> otherwise
     */
    public boolean canDelete() {
        try {
            if (deleteHandler == null) {
                return false;
            }

            if (canDeleteHandler != null) {
                return canDeleteHandler.test(this);
            } else {
                return true;
            }
        } catch (Exception e) {
            throw handleErrorInCallback(e, "canDeleteHandler");
        }
    }

    /**
     * Tries to delete this file.
     *
     * @return <tt>true</tt> if the operation was successful, <tt>false</tt> otherwise
     */
    public boolean tryDelete() {
        try {
            if (!canDelete()) {
                return false;
            }

            return deleteHandler.test(this);
        } catch (Exception e) {
            throw handleErrorInCallback(e, "deleteHandler");
        }
    }

    /**
     * Deletes this file.
     *
     * @throws HandledException if the file cannot be deleted
     */
    public void delete() {
        if (!tryDelete()) {
            throw Exceptions.createHandled().withNLSKey("VirtualFile.cannotDelete").set("file", path()).handle();
        }
    }

    /**
     * Determines if this file can (probably) be renamed.
     *
     * @return <tt>true</tt> if this file can be renamed or <tt>false</tt> otherwise
     */
    public boolean canRename() {
        try {
            if (renameHandler == null) {
                return false;
            }

            if (canRenameHandler != null) {
                return canRenameHandler.test(this);
            } else {
                return true;
            }
        } catch (Exception e) {
            throw handleErrorInCallback(e, "canRenameHandler");
        }
    }

    /**
     * Tries to rename this file.
     *
     * @param newName the new name of the file
     * @return <tt>true</tt> if the operation was successful, <tt>false</tt> otherwise
     */
    public boolean tryRename(String newName) {
        try {
            if (!canRename()) {
                return false;
            }

            return renameHandler.test(this, newName);
        } catch (Exception e) {
            throw handleErrorInCallback(e, "renameHandler");
        }
    }

    /**
     * Renames this file.
     *
     * @param newName the new name of the file
     * @throws HandledException if the file cannot be renamed
     */
    public void rename(String newName) {
        if (!tryRename(newName)) {
            throw Exceptions.createHandled().withNLSKey("VirtualFile.cannotRename").set("file", path()).handle();
        }
    }

    /**
     * Determines if the file exists.
     *
     * @return <tt>true</tt> if the file exists, <tt>false</tt> otherwise
     */
    public boolean exists() {
        try {
            if (existsFlagSupplier != null) {
                return existsFlagSupplier.test(this);
            }

            return false;
        } catch (Exception e) {
            throw handleErrorInCallback(e, "existsFlagSupplier");
        }
    }

    /**
     * Ensures that the given file exists.
     *
     * @return the file itself for fluent method calls
     * @throws HandledException if the file does not exist
     */
    public VirtualFile assertExists() {
        if (!exists()) {
            throw Exceptions.createHandled().withNLSKey("VirtualFile.fileDoesNotExist").set("file", path()).handle();
        }

        return this;
    }

    /**
     * Ensures that the given file exists as file
     *
     * @return the file itself for fluent method calls
     * @throws HandledException if the file does not exist or isn't a file
     */
    public VirtualFile assertExistingFile() {
        assertExists();

        if (isDirectory()) {
            throw Exceptions.createHandled().withNLSKey("VirtualFile.noFile").set("file", path()).handle();
        }

        return this;
    }

    /**
     * Ensures that the given file exists as directory
     *
     * @return the file itself for fluent method calls
     * @throws HandledException if the file does not exist or isn't a directory
     */
    public VirtualFile assertExistingDirectory() {
        if (!exists()) {
            throw Exceptions.createHandled()
                            .withNLSKey("VirtualFile.directoryDoesNotExist")
                            .set("file", path())
                            .handle();
        }
        if (!isDirectory()) {
            throw Exceptions.createHandled().withNLSKey("VirtualFile.noDirectory").set("file", path()).handle();
        }

        return this;
    }

    /**
     * Enumerates all children of this file.
     *
     * @param search the search which determines filtering and is responsible for collecting results
     */
    public void children(FileSearch search) {
        try {
            if (childProvider != null) {
                childProvider.enumerate(this, search);
            }
        } catch (Exception e) {
            throw handleErrorInCallback(e, "childProvider.enumerate");
        }
    }

    /**
     * Lists all children of this file.
     * <p>
     * Note that this will load all children into a list. In case of very large directories this might be slow
     * and memory consuming. Consider using {@link #children(FileSearch)} which supports filters and pagination.
     *
     * @return all child files as list
     */
    public TreeVisitorBuilder allChildren() {
        return tree().directChildrenOnly();
    }

    /**
     * Creates a configurable directory traversal visitor which visits all files and directories below this file.
     *
     * @return a new visitor which visits can be configured to visit all or a specific set of child files below
     * this file
     */
    public TreeVisitorBuilder tree() {
        return new TreeVisitorBuilder(this);
    }

    /**
     * Tries to find a child with the given name.
     * <p>
     * Note that the given file can be non-existent.
     *
     * @param name the name of the child to find
     * @return the child which may be a non-existing file
     */
    @Nonnull
    public VirtualFile findChild(String name) {
        try {
            if (childProvider != null) {
                VirtualFile child = childProvider.findChild(this, name);
                if (child != null) {
                    return child;
                }
            }

            return new MutableVirtualFile(this, name);
        } catch (Exception e) {
            throw handleErrorInCallback(e, "childProvider.findChild");
        }
    }

    /**
     * Tries to resolve the relative path within this directory.
     *
     * @param relativePath the path to resolve
     * @return the relative path wrapped as optional or an  empty optional if the given relaive path cannot be
     * resolved into a file.
     */
    @Nonnull
    public VirtualFile resolve(String relativePath) {
        String effectivePath = ensureRelativePath(relativePath);

        if (Strings.isEmpty(effectivePath)) {
            throw new IllegalArgumentException("Invalid path: " + effectivePath);
        }

        Tuple<String, String> nameAndRest = Strings.split(effectivePath, "/");
        VirtualFile child = findChild(nameAndRest.getFirst());
        if (Strings.isFilled(nameAndRest.getSecond())) {
            return child.resolve(nameAndRest.getSecond());
        }

        return child;
    }

    private String ensureRelativePath(String relativePath) {
        if (relativePath != null && relativePath.startsWith("/")) {
            return relativePath.substring(1);
        } else {
            return relativePath;
        }
    }

    /**
     * Determines if children can be created for this file.
     *
     * @return <tt>true</tt> if children can (probably) be created, <tt>false</tt> otherwise
     */
    public boolean canCreateChildren() {
        try {
            if (exists() && !isDirectory()) {
                return false;
            }

            if (canCreateChildrenHandler != null) {
                return canCreateChildrenHandler.test(this);
            }

            return true;
        } catch (Exception e) {
            throw handleErrorInCallback(e, "canCreateChildrenHandler");
        }
    }

    /**
     * Determines if this file can be created as new directory.
     *
     * @return <tt>true</tt> if this file can (probably) be created as directory
     */
    public boolean canCreateAsDirectory() {
        try {
            if (exists() && !isDirectory()) {
                return false;
            }

            if (createDirectoryHandler == null) {
                return false;
            }

            if (canCreateDirectoryHandler != null) {
                return canCreateDirectoryHandler.test(this);
            } else {
                return true;
            }
        } catch (Exception e) {
            throw handleErrorInCallback(e, "canCreateDirectoryHandler");
        }
    }

    /**
     * Tries to create this file as directory.
     *
     * @return <tt>true</tt> if the operation was successful, <tt>false</tt> otherwise
     */
    public boolean tryCreateAsDirectory() {
        try {
            if (exists()) {
                return isDirectory();
            }

            if (!canCreateAsDirectory()) {
                return false;
            }

            if (parent() != null && !parent().tryCreateAsDirectory()) {
                return false;
            }
            return createDirectoryHandler.test(this);
        } catch (Exception e) {
            throw handleErrorInCallback(e, "createDirectoryHandler");
        }
    }

    /**
     * Creates this file as new directory.
     *
     * @throws HandledException if the file cannot be created as new directory
     */
    public void createAsDirectory() {
        if (!tryCreateAsDirectory()) {
            throw Exceptions.createHandled()
                            .withNLSKey("VirtualFile.cannotCreateAsDirectory")
                            .set("file", path())
                            .handle();
        }
    }

    /**
     * Determines if an {@link OutputStream} can (probably) be created for this file.
     * <p>
     * Note that {@link #isWriteable()} is probably the better check.
     *
     * @return <tt>true</tt> if an output stream can be created, <tt>false</tt> otherwise
     */
    public boolean canCreateOutputStream() {
        if (internalCanCreateOutputStream()) {
            return true;
        }

        if (internalCanConsumeFile()) {
            return true;
        }

        return internalCanConsumeStream();
    }

    protected boolean internalCanCreateOutputStream() {
        try {
            return consumeStreamHandler != null && (canConsumeStream == null || canConsumeStream.test(this));
        } catch (Exception e) {
            throw handleErrorInCallback(e, "canConsumeStream");
        }
    }

    protected boolean internalCanConsumeStream() {
        try {
            return outputStreamSupplier != null && (canProvideOutputStream == null
                                                    || canProvideOutputStream.test(this));
        } catch (Exception e) {
            throw handleErrorInCallback(e, "canProvideOutputStream");
        }
    }

    protected boolean internalCanConsumeFile() {
        try {
            return consumeFileHandler != null && (canConsumeFile == null || canConsumeFile.test(this));
        } catch (Exception e) {
            throw handleErrorInCallback(e, "canConsumeFile");
        }
    }

    /**
     * Tries to create an output stream to write to the file.
     * <p>
     * Note that if the stream will be used to transfer data from a file or another stream, {@link #consumeFile(File)}
     * or {@link #consumeStream(InputStream, long)} should be used as these are likely to be more efficient.
     *
     * @return an output stream to provide the contents of the child or an empty optional if no output stream can be
     * created
     */
    public Optional<OutputStream> tryCreateOutputStream() {
        if (!canCreateOutputStream()) {
            return Optional.empty();
        }

        try {
            if (outputStreamSupplier != null) {
                return Optional.ofNullable(outputStreamSupplier.apply(this));
            }
        } catch (Exception e) {
            throw handleErrorInCallback(e, HANDLER_OUTPUT_STREAM_SUPPLIER);
        }

        if (consumeFileHandler != null) {
            try {
                return Optional.of(utils.createLocalBuffer(data -> {
                    try {
                        consumeFileHandler.accept(this, data);
                    } catch (Exception e) {
                        throw handleErrorInCallback(e, HANDLER_CONSUME_FILE_HANDLER);
                    }
                }));
            } catch (Exception e) {
                throw Exceptions.handle()
                                .to(StorageUtils.LOG)
                                .error(e)
                                .withSystemErrorMessage(
                                        "Layer 3/VFS: An error occurred in 'tryCreateOutputStream' of '%s': %s (%s)",
                                        path())
                                .handle();
            }
        }

        if (consumeStreamHandler != null) {
            try {
                return Optional.of(utils.createLocalBuffer(data -> {
                    try (FileInputStream in = new FileInputStream(data)) {
                        consumeStreamHandler.accept(this, Tuple.create(in, data.length()));
                    } catch (Exception e) {
                        throw handleErrorInCallback(e, HANDLER_CONSUME_FILE_HANDLER);
                    }
                }));
            } catch (Exception e) {
                throw Exceptions.handle()
                                .to(StorageUtils.LOG)
                                .error(e)
                                .withSystemErrorMessage(
                                        "Layer 3/VFS: An error occurred in 'tryCreateOutputStream' of '%s': %s (%s)",
                                        path())
                                .handle();
            }
        }

        return Optional.empty();
    }

    /**
     * Creates an output stream to write to the file.
     *
     * @return an output stream to provide the contents of the child
     * @throws HandledException if no output stream could be created
     */
    public OutputStream createOutputStream() {
        return tryCreateOutputStream().orElseThrow(this::createNotWritableError);
    }

    protected HandledException createNotWritableError() {
        return Exceptions.createHandled().withNLSKey("VirtualFile.cannotWrite").set("file", path()).handle();
    }

    /**
     * Determines if a given stream can be consumed to update the contents of this file.
     * <p>
     * Note that {@link #isWriteable()} is probably the better check.
     *
     * @return <tt>true</tt> if a stream can be consumed, <tt>false</tt> otherwise
     */
    public boolean canConsumeStream() {
        if (internalCanConsumeStream()) {
            return true;
        }

        if (internalCanCreateOutputStream()) {
            return true;
        }

        return internalCanConsumeFile();
    }

    /**
     * Tries to consume the given stream to update the contents of this file.
     * <p>
     * Note that if the source is a {@link File} {@link #consumeFile(File)} can be used which is likely to be more
     * efficient.
     *
     * @param inputStream the stream to read the contents from. Note that the caller has to close the stream itself.
     * @param length      the total number of bytes which will be provided via the given stream
     * @return <tt>true</tt> if the stream was consumed and the contents were updated, <tt>false</tt> otherwise
     */
    public boolean tryConsumeStream(InputStream inputStream, long length) {
        if (!canConsumeStream()) {
            return false;
        }

        if (consumeStreamHandler != null) {
            try {
                consumeStreamHandler.accept(this, Tuple.create(inputStream, length));
                return true;
            } catch (Exception e) {
                throw handleErrorInCallback(e, HANDLER_CONSUME_FILE_HANDLER);
            }
        }

        if (outputStreamSupplier != null) {
            try (OutputStream out = outputStreamSupplier.apply(this)) {
                Streams.transfer(inputStream, out);
                return true;
            } catch (Exception e) {
                throw handleErrorInCallback(e, HANDLER_OUTPUT_STREAM_SUPPLIER);
            }
        }

        if (consumeFileHandler != null) {
            try (OutputStream out = utils.createLocalBuffer(bufferedData -> {
                try {
                    consumeFileHandler.accept(this, bufferedData);
                } catch (Exception e) {
                    throw handleErrorInCallback(e, HANDLER_CONSUME_FILE_HANDLER);
                }
            })) {
                Streams.transfer(inputStream, out);
                return true;
            } catch (Exception e) {
                throw Exceptions.handle()
                                .to(StorageUtils.LOG)
                                .error(e)
                                .withSystemErrorMessage(
                                        "Layer 3/VFS: An error occurred in 'tryConsumeStream' of '%s': %s (%s)",
                                        path())
                                .handle();
            }
        }

        return false;
    }

    /**
     * Consumes the given stream to update the contents of this file or throws an exception if this fails.
     * <p>
     * Note that if the source is a {@link File} {@link #consumeFile(File)} can be used which is likely to be more
     * efficient.
     *
     * @param inputStream the stream to read the contents from
     * @param length      the total number of bytes which will be provided via the given stream
     * @throws HandledException if the stream cannot be consumed
     */
    public void consumeStream(InputStream inputStream, long length) {
        if (!tryConsumeStream(inputStream, length)) {
            throw createNotWritableError();
        }
    }

    /**
     * Determines if a given file can be consumed to update the contents of this file.
     * <p>
     * Note that {@link #isWriteable()} is probably the better check.
     *
     * @return <tt>true</tt> if a file can be consumed, <tt>false</tt> otherwise
     */
    public boolean canConsumeFile() {
        if (internalCanConsumeFile()) {
            return true;
        }

        if (internalCanCreateOutputStream()) {
            return true;
        }

        return internalCanConsumeStream();
    }

    /**
     * Tries to consume the given file to update the contents of this file.
     *
     * @param data the file to read the contents from
     * @return <tt>true</tt> if the file was consumed and the contents were updated, <tt>false</tt> otherwise
     */
    public boolean tryConsumeFile(File data) {
        if (!canConsumeFile()) {
            return false;
        }

        if (consumeFileHandler != null) {
            try {
                consumeFileHandler.accept(this, data);
            } catch (Exception e) {
                throw handleErrorInCallback(e, HANDLER_CONSUME_FILE_HANDLER);
            }
        }

        if (consumeStreamHandler != null) {
            try (FileInputStream in = new FileInputStream(data)) {
                consumeStreamHandler.accept(this, Tuple.create(in, data.length()));
                return true;
            } catch (Exception e) {
                throw handleErrorInCallback(e, HANDLER_CONSUME_FILE_HANDLER);
            }
        }

        if (outputStreamSupplier != null) {
            try (OutputStream out = outputStreamSupplier.apply(this); FileInputStream in = new FileInputStream(data)) {
                Streams.transfer(in, out);
                return true;
            } catch (Exception e) {
                throw handleErrorInCallback(e, HANDLER_OUTPUT_STREAM_SUPPLIER);
            }
        }

        return false;
    }

    /**
     * Consumes the given file to update the contents of this file or throws an exception if this fails.
     *
     * @param data the file to read the contents from
     * @throws HandledException if the file cannot be consumed
     */
    public void consumeFile(File data) {
        if (!tryConsumeFile(data)) {
            throw createNotWritableError();
        }
    }

    /**
     * Determines if the file is readable.
     *
     * @return <tt>true</tt> if the file is readable, <tt>false</tt> otherwise
     */
    public boolean isReadable() {
        return !isDirectory() && canCreateInputStream();
    }

    /**
     * Determines if the file is writeable.
     *
     * @return <tt>true</tt> if the file is writeable, <tt>false</tt> otherwise
     */
    public boolean isWriteable() {
        return !isDirectory() && canCreateOutputStream();
    }

    /**
     * Determines if an {@link InputStream} can (probably) be created for this file.
     * <p>
     * Note that {@link #isReadable()} is probably the better check.
     *
     * @return <tt>true</tt> if an output stream can be created, <tt>false</tt> otherwise
     */
    public boolean canCreateInputStream() {
        try {
            if (inputStreamSupplier == null) {
                return false;
            }

            if (canProvideInputStream != null) {
                return canProvideInputStream.test(this);
            } else {
                return true;
            }
        } catch (Exception e) {
            throw handleErrorInCallback(e, "canProvideInputStream");
        }
    }

    /**
     * Tries to create an input stream to read the contents of the file.
     *
     * @return an input stream to read the contents of the file or an empty optional if no input stream can be created
     */
    public Optional<InputStream> tryCreateInputStream() {
        try {
            if (!canCreateInputStream()) {
                return Optional.empty();
            }

            return Optional.ofNullable(inputStreamSupplier.apply(this));
        } catch (Exception e) {
            throw handleErrorInCallback(e, "inputStreamSupplier");
        }
    }

    /**
     * Creates an input stream to write to the file.
     *
     * @return an input stream to read the contents of the file
     * @throws HandledException if no input stream could be created
     */
    public InputStream createInputStream() {
        return tryCreateInputStream().orElseThrow(() -> Exceptions.createHandled()
                                                                  .withNLSKey("VirtualFile.cannotRead")
                                                                  .set("file", path())
                                                                  .handle());
    }

    /**
     * Determines if the content can be provided as {@link FileHandle}.
     *
     * @return <tt>true</tt> if a the contents of this file can be provided as file handle, <tt>false</tt> otherwise
     */
    public boolean canDownload() {
        try {
            if (fileHandleSupplier == null && !canCreateInputStream()) {
                return false;
            }

            if (canProvideFileHandle != null) {
                return canProvideFileHandle.test(this);
            } else {
                return true;
            }
        } catch (Exception e) {
            throw handleErrorInCallback(e, "canProvidFileHandle");
        }
    }

    /**
     * Tries to provides the contents of this file as {@link FileHandle}.
     *
     * @return a <tt>FileHandle</tt> with the contents of the file or an empty optional if no handle can be obtained
     */
    public Optional<FileHandle> tryDownload() {
        try {
            if (!canDownload()) {
                return Optional.empty();
            }
            if (fileHandleSupplier == null) {
                return tryManualDownload();
            } else {
                return Optional.ofNullable(fileHandleSupplier.apply(this));
            }
        } catch (Exception e) {
            throw handleErrorInCallback(e, "fileHandleSupplier");
        }
    }

    private Optional<FileHandle> tryManualDownload() throws IOException {
        File temporaryFile = File.createTempFile("vfs-", null);
        try {
            try (FileOutputStream out = new FileOutputStream(temporaryFile); InputStream in = createInputStream()) {
                Streams.transfer(in, out);
            }

            return Optional.of(FileHandle.temporaryFileHandle(temporaryFile));
        } catch (IOException e) {
            Files.delete(temporaryFile);
            throw e;
        }
    }

    /**
     * Provides the contents of this file as {@link FileHandle}.
     *
     * @return a file handle representing the contents of this file
     * @throws HandledException if no file handle could be obtained
     */
    public FileHandle download() {
        return tryDownload().orElseThrow(() -> Exceptions.createHandled()
                                                         .withNLSKey("VirtualFile.cannotRead")
                                                         .set("file", path())
                                                         .handle());
    }

    /**
     * Determines if this file can (probably) be move in an efficient way.
     *
     * @param newParent the new parent directory
     * @return <tt>true</tt> if this file can be efficiently moved or <tt>false</tt> otherwise
     */
    public boolean canFastMoveTo(VirtualFile newParent) {
        try {
            if (fastMoveHandler == null) {
                return false;
            }

            if (!exists() || !newParent.exists() || !newParent.isDirectory()) {
                return false;
            }

            if (canFastMoveHandler != null) {
                return canFastMoveHandler.test(this, newParent);
            } else {
                return true;
            }
        } catch (Exception e) {
            throw handleErrorInCallback(e, "canFastMoveHandler");
        }
    }

    /**
     * Determines if this file can be moved (either via an efficient implementation or by copy+delete).
     *
     * @return <tt>true</tt> if the file can (probably) be moved, <tt>false</tt> otherwise
     */
    public boolean canMove() {
        return canDelete() && (isDirectory() || canCreateInputStream());
    }

    /**
     * Tries to efficiently move the file in the given directory.
     * <p>
     * This is only an interal API as the public API is accessible via {@link #transferTo(VirtualFile)}.
     *
     * @param newParent the new parent directory
     * @return <tt>true</tt> if the operation was successful, <tt>false</tt> otherwise
     */
    protected boolean tryFastMoveTo(VirtualFile newParent) {
        try {
            if (!canFastMoveTo(newParent)) {
                return false;
            }

            return fastMoveHandler.test(this, newParent);
        } catch (Exception e) {
            throw handleErrorInCallback(e, "fastMoveHandler");
        }
    }

    /**
     * Provides various ways of copying or moving the contents of this file to the given destination.
     *
     * @param destination the desntiantion to transer the contents to
     * @return a helper which permits to transfer the contents of this file to the given destination
     */
    @CheckReturnValue
    public Transfer transferTo(VirtualFile destination) {
        return new Transfer(this, destination);
    }

    /**
     * Transfers the contents of this file into the given blob.
     * <p>
     * This will support retries in case the transfer is interrupted. Also, this will set the filename of the
     * destination blob to <tt>name()</tt>.
     *
     * @param destination the blob to write the contents to
     */
    public void transferFileToBlob(Blob destination) {
        transferFileToBlob(name(), destination);
    }

    /**
     * Transfers the contents of this file into the given blob.
     * <p>
     * This will support retries in case the transfer is interrupted.
     *
     * @param filename    the filename to specify for the destination blob or <tt>null</tt> to leave it unchanged
     * @param destination the blob to write the contents to
     */
    public void transferFileToBlob(@Nullable String filename, Blob destination) {
        for (Attempt attempt : Attempt.values()) {
            try (InputStream input = createInputStream()) {
                destination.updateContent(filename, input, size());
                return;
            } catch (Exception e) {
                if (attempt.shouldThrow(e)) {
                    throw Exceptions.handle()
                                    .to(StorageUtils.LOG)
                                    .error(e)
                                    .withSystemErrorMessage(
                                            "Layer 3/VFS: An error occurred when transferting '%s' to %s in '%s': %s (%s)",
                                            path(),
                                            destination.getStorageSpace().getName(),
                                            destination.getBlobKey())
                                    .handle();
                }
            }
        }
    }

    /**
     * Delivers the contents of this file into the given web context.
     *
     * @param webContext the HTTP request to respond to
     */
    public void deliverTo(WebContext webContext) {
        try {
            if (!exists() || isDirectory()) {
                webContext.respondWith().error(HttpResponseStatus.NOT_FOUND);
                return;
            }

            tunnelHandler.accept(this, webContext.respondWith().download(name()).notCached());
        } catch (Exception e) {
            webContext.respondWith()
                      .error(HttpResponseStatus.INTERNAL_SERVER_ERROR, handleErrorInCallback(e, "tunnelHandler"));
        }
    }

    /**
     * Delivers the contents of this file as download.
     *
     * @param webContext the HTTP request to respond to
     */
    public void deliverDownloadTo(WebContext webContext) {
        try {
            if (!exists() || isDirectory()) {
                webContext.respondWith().error(HttpResponseStatus.NOT_FOUND);
                return;
            }

            tunnelHandler.accept(this, webContext.respondWith().named(name()).notCached());
        } catch (Exception e) {
            webContext.respondWith()
                      .error(HttpResponseStatus.INTERNAL_SERVER_ERROR, handleErrorInCallback(e, "tunnelHandler"));
        }
    }

    private static void defaultTunnelHandler(VirtualFile file, Response response) {
        try (InputStream from = file.createInputStream()) {
            if (from == null) {
                response.error(HttpResponseStatus.NOT_FOUND);
                return;
            }

            try (OutputStream to = response.outputStream(HttpResponseStatus.OK,
                                                         MimeHelper.guessMimeType(file.name()))) {
                Streams.transfer(from, to);
            }
        } catch (IOException e) {
            response.error(HttpResponseStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @Override
    public String toString() {
        return path();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }

        if ((other instanceof VirtualFile) && (Objects.equals(((VirtualFile) other).parent, parent))) {
            return Objects.equals(name, ((VirtualFile) other).name);
        }

        return false;
    }

    @Override
    public int hashCode() {
        return path().hashCode();
    }

    @Override
    public int compareTo(VirtualFile other) {
        if (other == null) {
            return 1;
        }

        return Objects.compare(path(), other.path(), String::compareTo);
    }
}
