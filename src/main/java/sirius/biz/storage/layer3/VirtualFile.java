/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.storage.layer1.FileHandle;
import sirius.biz.storage.layer2.Blob;
import sirius.biz.storage.layer2.Directory;
import sirius.biz.storage.util.Attempt;
import sirius.biz.storage.util.StorageUtils;
import sirius.kernel.async.TaskContext;
import sirius.kernel.commons.Files;
import sirius.kernel.commons.Outcall;
import sirius.kernel.commons.Streams;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Wait;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.PriorityParts;
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
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
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
 * As in most cases nearly all the functions will be delegated to other classes, this class uses callbacks instead of
 * a proper class hierarchy.
 */
public abstract class VirtualFile extends Composable implements Comparable<VirtualFile> {

    private static final String HANDLER_OUTPUT_STREAM_SUPPLIER = "outputStreamSupplier";
    private static final String HANDLER_CONSUME_FILE_HANDLER = "consumeFileHandler";
    private static final String MESSAGE_KEY_LOAD_FROM_URL_FAILED = "$VirtualFile.loadFromUrlFailed";
    private static final String MESSAGE_KEY_LOAD_FROM_URL_DISABLED = "$VirtualFile.loadFromUrlDisabled";

    protected String name;
    protected String description;
    protected String htmlDescription;
    protected VirtualFile parent;
    protected ChildProvider childProvider;
    protected ToLongFunction<VirtualFile> lastModifiedSupplier;
    protected ToLongFunction<VirtualFile> sizeSupplier;
    protected Predicate<VirtualFile> directoryFlagSupplier;
    protected Predicate<VirtualFile> existsFlagSupplier;
    protected Predicate<VirtualFile> readOnlyFlagSupplier;
    protected BiPredicate<VirtualFile, Boolean> readOnlyHandler;
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
    protected Consumer<VirtualFile> touchHandler;

    @Part
    private static StorageUtils utils;

    @PriorityParts(RemoteFileResolver.class)
    private static List<RemoteFileResolver> remoteFileResolvers;

    @ConfigValue("storage.layer3.retriesForServiceUnavailable")
    private static int retriesForServiceUnavailable;

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
        } catch (Exception exception) {
            throw handleErrorInCallback(exception, "directoryFlagSupplier");
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

    private HandledException handleErrorInCallback(Exception exception, String callback) {
        return Exceptions.handle()
                         .to(StorageUtils.LOG)
                         .error(exception)
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
     * If this be <tt>/foo/bar/baz</tt> and the given root parent is <tt>/foo</tt> then this would
     * return <tt>bar/baz</tt>. Therefore, this is the inverse of {@link #resolve(String)}.
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
            result.addFirst(current);
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
            result.addFirst(current);
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
     * Returns a short description, containing html tags, of the file.
     *
     * @return the description of the file, containing html tags
     */
    @Nullable
    public String htmlDescription() {
        return htmlDescription;
    }

    /**
     * Returns the last modification timestamp.
     *
     * @return the last modification as in {@link File#lastModified()}
     */
    public long lastModified() {
        try {
            return lastModifiedSupplier == null ? 0 : lastModifiedSupplier.applyAsLong(this);
        } catch (Exception exception) {
            throw handleErrorInCallback(exception, "lastModifiedSupplier");
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
                return Instant.ofEpochMilli(lastModified).atZone(ZoneId.systemDefault()).toLocalDateTime();
            }
        } catch (Exception exception) {
            throw handleErrorInCallback(exception, "lastModifiedSupplier");
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
        } catch (Exception exception) {
            throw handleErrorInCallback(exception, "sizeSupplier");
        }
    }

    /**
     * Determines if this file can (probably) be deleted.
     *
     * @return <tt>true</tt> if this file can be deleted or <tt>false</tt> otherwise
     */
    public boolean canDelete() {
        return canDelete(false);
    }

    /**
     * Determines if this file can (probably) be deleted.
     *
     * @param force if <tt>true</tt>, we signalize the file can potentially be deleted even if it is read-only
     * @return <tt>true</tt> if this file can be deleted or <tt>false</tt> otherwise
     */
    public boolean canDelete(boolean force) {
        try {
            if (readOnly() && !force) {
                return false;
            }

            if (deleteHandler == null) {
                return false;
            }

            if (canDeleteHandler != null) {
                return canDeleteHandler.test(this);
            } else {
                return true;
            }
        } catch (Exception exception) {
            throw handleErrorInCallback(exception, "canDeleteHandler");
        }
    }

    /**
     * Tries to delete this file.
     *
     * @param force if <tt>true</tt> the file will be deleted even if it is read-only
     * @return <tt>true</tt> if the operation was successful, <tt>false</tt> otherwise
     */
    public boolean tryDelete(boolean force) {
        try {
            if (!canDelete(force)) {
                return false;
            }

            return deleteHandler.test(this);
        } catch (Exception exception) {
            throw handleErrorInCallback(exception, "deleteHandler");
        }
    }

    /**
     * Deletes this file.
     *
     * @throws HandledException if the file cannot be deleted
     */
    public void delete() {
        if (!tryDelete(false)) {
            throw Exceptions.createHandled().withNLSKey("VirtualFile.cannotDelete").set("file", path()).handle();
        }
    }

    /**
     * Deletes this file, also if it's read-only.
     * <p>
     * Depending on the underlying storage, a deletion is not always possible if the file is set as read-only.
     *
     * @throws HandledException if the file cannot be deleted
     */
    public void forceDelete() {
        if (!tryDelete(true)) {
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
            if (renameHandler == null || readOnly()) {
                return false;
            }

            if (canRenameHandler != null) {
                return canRenameHandler.test(this);
            } else {
                return true;
            }
        } catch (Exception exception) {
            throw handleErrorInCallback(exception, "canRenameHandler");
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
        } catch (Exception exception) {
            throw handleErrorInCallback(exception, "renameHandler");
        }
    }

    /**
     * Tries to set the read-only flag of this file.
     *
     * @param readOnly the value of the read-only flag
     * @return <tt>true</tt> if the operation was successful, <tt>false</tt> otherwise
     */
    public boolean updateReadOnlyFlag(boolean readOnly) {
        try {
            if (readOnlyHandler == null) {
                return false;
            }

            return readOnlyHandler.test(this, readOnly);
        } catch (Exception exception) {
            throw handleErrorInCallback(exception, "readOnlyHandler");
        }
    }

    /**
     * Renames this file.
     *
     * @param newName the new name of the file
     * @throws HandledException if the file cannot be renamed
     */
    public void rename(String newName) {
        if (utils.containsIllegalFileChars(newName)) {
            throw Exceptions.createHandled()
                            .withNLSKey("VirtualFile.cannotRename.invalidName")
                            .set("file", path())
                            .handle();
        }
        if (!tryRename(newName)) {
            throw Exceptions.createHandled().withNLSKey("VirtualFile.cannotRename").set("file", path()).handle();
        }
    }

    /**
     * Tries to "touch" this file.
     * <p>
     * This will attempt to set the {@linkplain #lastModified() last modified date} to <tt>now</tt>. Note however, that
     * only some underlying providers will support this. If the call is not supported, nothing will happen.
     */
    public void tryTouch() {
        try {
            if (touchHandler != null) {
                touchHandler.accept(this);
            }
        } catch (Exception exception) {
            throw handleErrorInCallback(exception, "touchHandler");
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
        } catch (Exception exception) {
            throw handleErrorInCallback(exception, "existsFlagSupplier");
        }
    }

    /**
     * Determines if the file is read-only.
     *
     * @return <tt>true</tt> if the file is read-only, <tt>false</tt> otherwise
     */
    public boolean readOnly() {
        try {
            if (readOnlyFlagSupplier == null) {
                return false;
            }

            return readOnlyFlagSupplier.test(this);
        } catch (Exception exception) {
            throw handleErrorInCallback(exception, "readOnlyFlagSupplier");
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
        } catch (Exception exception) {
            throw handleErrorInCallback(exception, "childProvider.enumerate");
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
        } catch (Exception exception) {
            throw handleErrorInCallback(exception, "childProvider.findChild");
        }
    }

    /**
     * Tries to resolve the relative path within this directory.
     *
     * @param relativePath the path to resolve
     * @return the relative path wrapped as a VirtualFile
     * @throws IllegalArgumentException when the given path is empty after sanitization
     */
    @Nonnull
    public VirtualFile resolve(String relativePath) {
        String sanitizedPath = utils.sanitizePath(relativePath);

        if (Strings.isEmpty(sanitizedPath)) {
            throw new IllegalArgumentException("Invalid path: " + sanitizedPath);
        }

        Tuple<String, String> nameAndRest = Strings.split(sanitizedPath, "/");
        VirtualFile child = findChild(nameAndRest.getFirst());
        if (Strings.isFilled(nameAndRest.getSecond())) {
            return child.resolve(nameAndRest.getSecond());
        }

        return child;
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

            if (canCreateChildrenHandler == null) {
                return true;
            }

            return canCreateChildrenHandler.test(this);
        } catch (Exception exception) {
            throw handleErrorInCallback(exception, "canCreateChildrenHandler");
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
        } catch (Exception exception) {
            throw handleErrorInCallback(exception, "canCreateDirectoryHandler");
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
        } catch (Exception exception) {
            throw handleErrorInCallback(exception, "createDirectoryHandler");
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
        } catch (Exception exception) {
            throw handleErrorInCallback(exception, "canConsumeStream");
        }
    }

    protected boolean internalCanConsumeStream() {
        try {
            return outputStreamSupplier != null && (canProvideOutputStream == null
                                                    || canProvideOutputStream.test(this));
        } catch (Exception exception) {
            throw handleErrorInCallback(exception, "canProvideOutputStream");
        }
    }

    protected boolean internalCanConsumeFile() {
        try {
            return consumeFileHandler != null && (canConsumeFile == null || canConsumeFile.test(this));
        } catch (Exception exception) {
            throw handleErrorInCallback(exception, "canConsumeFile");
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
        if (!canCreateOutputStream() || readOnly()) {
            return Optional.empty();
        }

        try {
            if (outputStreamSupplier != null) {
                return Optional.ofNullable(outputStreamSupplier.apply(this));
            }
        } catch (Exception exception) {
            throw handleErrorInCallback(exception, HANDLER_OUTPUT_STREAM_SUPPLIER);
        }

        if (consumeFileHandler != null) {
            try {
                return Optional.of(utils.createLocalBuffer(data -> {
                    try {
                        consumeFileHandler.accept(this, data);
                    } catch (Exception exception) {
                        throw handleErrorInCallback(exception, HANDLER_CONSUME_FILE_HANDLER);
                    }
                }));
            } catch (Exception exception) {
                throw Exceptions.handle()
                                .to(StorageUtils.LOG)
                                .error(exception)
                                .withSystemErrorMessage(
                                        "Layer 3/VFS: An error occurred in 'tryCreateOutputStream' of '%s': %s (%s)",
                                        path())
                                .handle();
            }
        }

        if (consumeStreamHandler != null) {
            try {
                return Optional.of(utils.createLocalBuffer(data -> {
                    try (FileInputStream inputStream = new FileInputStream(data)) {
                        consumeStreamHandler.accept(this, Tuple.create(inputStream, data.length()));
                    } catch (Exception exception) {
                        throw handleErrorInCallback(exception, HANDLER_CONSUME_FILE_HANDLER);
                    }
                }));
            } catch (Exception exception) {
                throw Exceptions.handle()
                                .to(StorageUtils.LOG)
                                .error(exception)
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
        if (readOnly()) {
            return false;
        }

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
            } catch (Exception exception) {
                throw handleErrorInCallback(exception, HANDLER_CONSUME_FILE_HANDLER);
            }
        }

        if (outputStreamSupplier != null) {
            try (OutputStream outputStream = outputStreamSupplier.apply(this)) {
                Streams.transfer(inputStream, outputStream);
                return true;
            } catch (Exception exception) {
                throw handleErrorInCallback(exception, HANDLER_OUTPUT_STREAM_SUPPLIER);
            }
        }

        if (consumeFileHandler != null) {
            try (OutputStream outputStream = utils.createLocalBuffer(bufferedData -> {
                try {
                    consumeFileHandler.accept(this, bufferedData);
                } catch (Exception exception) {
                    throw handleErrorInCallback(exception, HANDLER_CONSUME_FILE_HANDLER);
                }
            })) {
                Streams.transfer(inputStream, outputStream);
                return true;
            } catch (Exception exception) {
                throw Exceptions.handle()
                                .to(StorageUtils.LOG)
                                .error(exception)
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
        if (readOnly()) {
            return false;
        }

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
            } catch (Exception exception) {
                throw handleErrorInCallback(exception, HANDLER_CONSUME_FILE_HANDLER);
            }
        }

        if (consumeStreamHandler != null) {
            try (FileInputStream inputStream = new FileInputStream(data)) {
                consumeStreamHandler.accept(this, Tuple.create(inputStream, data.length()));
                return true;
            } catch (Exception exception) {
                throw handleErrorInCallback(exception, HANDLER_CONSUME_FILE_HANDLER);
            }
        }

        if (outputStreamSupplier != null) {
            try (OutputStream outputStream = outputStreamSupplier.apply(this);
                 FileInputStream inputStream = new FileInputStream(data)) {
                Streams.transfer(inputStream, outputStream);
                return true;
            } catch (Exception exception) {
                throw handleErrorInCallback(exception, HANDLER_OUTPUT_STREAM_SUPPLIER);
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
        return !isDirectory() && !readOnly() && canCreateOutputStream();
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
        } catch (Exception exception) {
            throw handleErrorInCallback(exception, "canProvideInputStream");
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
        } catch (Exception exception) {
            throw handleErrorInCallback(exception, "inputStreamSupplier");
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
     * @return <tt>true</tt> if the contents of this file can be provided as file handle, <tt>false</tt> otherwise
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
        } catch (Exception exception) {
            throw handleErrorInCallback(exception, "canProvideFileHandle");
        }
    }

    /**
     * Tries to provide the contents of this file as {@link FileHandle}.
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
        } catch (Exception exception) {
            throw handleErrorInCallback(exception, "fileHandleSupplier");
        }
    }

    private Optional<FileHandle> tryManualDownload() throws IOException {
        File temporaryFile = File.createTempFile("vfs-", null);
        try {
            try (FileOutputStream outputStream = new FileOutputStream(temporaryFile);
                 InputStream inputStream = createInputStream()) {
                Streams.transfer(inputStream, outputStream);
            }

            return Optional.of(FileHandle.temporaryFileHandle(temporaryFile));
        } catch (IOException exception) {
            Files.delete(temporaryFile);
            throw exception;
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
     * Determines if this file can (probably) be moved in an efficient way.
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
        } catch (Exception exception) {
            throw handleErrorInCallback(exception, "canFastMoveHandler");
        }
    }

    /**
     * Determines if this file can be moved (either via an efficient implementation or by copy+delete).
     *
     * @return <tt>true</tt> if the file can (probably) be moved, <tt>false</tt> otherwise
     */
    public boolean canMove() {
        return !readOnly() && canDelete() && (isDirectory() || canCreateInputStream());
    }

    /**
     * Tries to efficiently move the file in the given directory.
     * <p>
     * This is only an internal API as the public API is accessible via {@link #transferTo(VirtualFile)}.
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
        } catch (Exception exception) {
            throw handleErrorInCallback(exception, "fastMoveHandler");
        }
    }

    /**
     * Provides various ways of copying or moving the contents of this file to the given destination.
     *
     * @param destination the destination to transfer the contents to
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
            } catch (Exception exception) {
                if (attempt.shouldThrow(exception)) {
                    throw Exceptions.handle()
                                    .to(StorageUtils.LOG)
                                    .error(exception)
                                    .withSystemErrorMessage(
                                            "Layer 3/VFS: An error occurred when transferring '%s' to %s in '%s': %s (%s)",
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

            tunnelHandler.accept(this, webContext.respondWith().named(name()).notCached());
        } catch (Exception exception) {
            webContext.respondWith()
                      .error(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                             handleErrorInCallback(exception, "tunnelHandler"));
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

            tunnelHandler.accept(this, webContext.respondWith().download(name()).notCached());
        } catch (Exception exception) {
            webContext.respondWith()
                      .error(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                             handleErrorInCallback(exception, "tunnelHandler"));
        }
    }

    private static void defaultTunnelHandler(VirtualFile file, Response response) {
        try (InputStream inputStream = file.createInputStream()) {
            if (inputStream == null) {
                response.error(HttpResponseStatus.NOT_FOUND);
                return;
            }

            try (OutputStream outputStream = response.outputStream(HttpResponseStatus.OK,
                                                                   MimeHelper.guessMimeType(file.name()))) {
                Streams.transfer(inputStream, outputStream);
            }
        } catch (IOException exception) {
            response.error(HttpResponseStatus.INTERNAL_SERVER_ERROR, exception.getMessage());
        }
    }

    /**
     * Fetches the given URL and stores the contents in this file.
     * <p>
     * If the contents of the file are not newer than the last modification date of this file, nothing will happen
     * unless the <tt>force</tt> parameter is set to <tt>true</tt>.
     * <p>
     * This will increment one of the timings (downloaded or download skipped) and also directly report IO
     * errors to the process without spamming the system logs.
     *
     * @param url  the URL to fetch
     * @param mode determines under which conditions the data from the given URL should be fetched
     * @return <tt>true</tt> if the file was successfully fetched or <tt>false</tt> if the contents weren't updated
     * as no change was detected
     * @throws HandledException in case of any error while downloading the contents
     */
    public boolean loadFromUrl(URI url, FetchFromUrlMode mode) {
        Watch watch = Watch.start();
        if (performLoadFromUri(url, mode)) {
            TaskContext.get().addTiming(NLS.get("VirtualFile.fileDownloaded"), watch.elapsedMillis());
            return true;
        } else {
            TaskContext.get().addTiming(NLS.get("VirtualFile.fileDownloadSkipped"), watch.elapsedMillis());
            return false;
        }
    }

    /**
     * Fetches the given URI and stores the contents in this file.
     * <p>
     * Note that this will not increment any timings, use {@link #loadFromUrl(URI, FetchFromUrlMode)} instead if
     * required.
     *
     * @param uri  the URI to fetch
     * @param mode determines under which conditions the data from the given URI should be fetched
     * @return <tt>true</tt> if the file was successfully read, or <tt>false</tt> if the server answered with code
     * {@linkplain HttpResponseStatus#NOT_MODIFIED 304 Not Modified}
     */
    public boolean performLoadFromUri(URI uri, FetchFromUrlMode mode) {
        try {
            if (mode == FetchFromUrlMode.NEVER_FETCH) {
                return false;
            }
            if (exists() && shouldSkipDownloadForExistingFile(mode)) {
                return false;
            }

            HttpResponse<InputStream> response = requestFileFromUri(uri, mode, retriesForServiceUnavailable);

            if (response.statusCode() == HttpResponseStatus.NOT_MODIFIED.code()) {
                tryTouch();
                return false;
            }

            loadFromResponse(response);

            return true;
        } catch (IOException exception) {
            throw Exceptions.createHandled()
                            .error(exception)
                            .withNLSKey("VirtualFile.downloadFailed")
                            .set("url", uri)
                            .hint(ProcessLog.HINT_MESSAGE_TYPE, MESSAGE_KEY_LOAD_FROM_URL_FAILED)
                            .hint(ProcessLog.HINT_MESSAGE_COUNT, ProcessLog.MESSAGE_TYPE_COUNT_MEDIUM)
                            .handle();
        }
    }

    /**
     * Returns the underlying id of this virtual file, if it is backed by a {@link Directory} or a {@link Blob}.
     * <p>
     * For a directory, this returns the directory ID as string.
     * For a file, this returns the blob key.
     *
     * @return the underlying id or <tt>null</tt> if this file is not backed by a directory or blob.
     */
    public String getUnderlyingId() {
        if (exists() && isDirectory()) {
            return tryAs(Directory.class).map(Directory::getIdAsString).orElse(null);
        } else if (exists() && isFile()) {
            return tryAs(Blob.class).map(Blob::getBlobKey).orElse(null);
        } else {
            return null;
        }
    }

    private HttpResponse<InputStream> requestFileFromUri(URI uri, FetchFromUrlMode mode, int retries)
            throws IOException {
        Outcall outcall = new Outcall(uri);
        outcall.alwaysFollowRedirects();

        CookieManager cookieManager = new CookieManager();
        outcall.modifyClient(null).cookieHandler(cookieManager);

        if (mode != FetchFromUrlMode.ALWAYS_FETCH && exists() && lastModifiedDate() != null) {
            outcall.setIfModifiedSince(lastModifiedDate());
        }

        HttpResponse<InputStream> response = outcall.getResponse();

        if (response.statusCode() == HttpResponseStatus.SERVICE_UNAVAILABLE.code()) {
            Streams.exhaust(response.body());

            if (retries > 0) {
                // Wait 200ms, 700ms, 1200ms...
                Wait.millis(200 + (retriesForServiceUnavailable - retries) * 500);
                return requestFileFromUri(uri, mode, retries - 1);
            }

            throw new IOException(Strings.apply("The server responded with status %s (%s) after %s retries!",
                                                HttpResponseStatus.valueOf(response.statusCode()).toString(),
                                                response.statusCode(),
                                                retriesForServiceUnavailable));
        }

        if (response.statusCode() >= 400) {
            Streams.exhaust(response.body());
            throw new IOException(Strings.apply("The server responded with status %s (%s)!",
                                                HttpResponseStatus.valueOf(response.statusCode()).toString(),
                                                response.statusCode()));
        }

        return response;
    }

    private boolean shouldSkipDownloadForExistingFile(FetchFromUrlMode mode) {
        // If the mode is set to non-existent files only, we perform no download if the file exists.
        // We also perform no download, if the file has already been modified (downloaded) at the same day
        // (unless the mode is set to ALWAYS_FETCH...)
        return mode == FetchFromUrlMode.NON_EXISTENT || (mode == FetchFromUrlMode.NON_EXISTENT_OR_MODIFIED
                                                         && lastModifiedDate().isAfter(LocalDate.now().atStartOfDay()));
    }

    /**
     * Loads the contents of the given response into this file.
     *
     * @param response the response to read the contents from
     * @throws IOException in case of an IO error
     */
    public void loadFromResponse(HttpResponse<InputStream> response) throws IOException {
        assertResponseNotHtml(response);

        long length = response.headers().firstValueAsLong(HttpHeaderNames.CONTENT_LENGTH.toString()).orElse(-1);
        if (length >= 0) {
            consumeStream(response.body(), length);
        } else {
            try (OutputStream outputStream = createOutputStream()) {
                Streams.transfer(response.body(), outputStream);
            }
        }
    }

    private void assertResponseNotHtml(HttpResponse<InputStream> response) throws IOException {
        List<String> contentTypes = response.headers().allValues(HttpHeaderNames.CONTENT_TYPE.toString());

        if (contentTypes.stream()
                        .anyMatch(contentType -> contentType.contains(HttpHeaderValues.TEXT_HTML.toString()))) {
            Streams.exhaust(response.body());
            throw new IOException(Strings.apply("The server response contains an unexpected content-type (%s)!",
                                                String.join(", ", contentTypes)));
        }
    }

    /**
     * Attempts to resolve the file from the given URL or performs a download if the file does not exist.
     * <p>
     * Uses the path of the given URL relative to this directory and tries to resolve the child file. If this file
     * does not exist, or has been modified since its last download, a download will be attempted.
     * <p>
     * As a result, the resolved file will be returned (which was either already there or has been downloaded).
     * <p>
     * In order to determine the effective filename/path of the given URL we use the registered
     * {@linkplain RemoteFileResolver remote file resolvers}.
     * <p>
     * This will increment one of the timings (downloaded or download skipped) and also directly report IO
     * errors to the process without spamming the system logs.
     *
     * @param url                   the url to fetch
     * @param mode                  determines under which conditions the data from the given URL should be fetched
     * @param fileExtensionVerifier specifies which extensions are accepted. This should be used to prevent using
     *                              ".php" or the like as effective file name. When in doubt, use
     *                              {@link #notServerSidedScripting(String)} to at least exclude common server-sided
     *                              scripting languages like PHP.
     * @return the file which has been resolved (and downloaded if necessary) along with a flag which indicates if an
     * update (download) has been performed
     * @throws HandledException in case of an any error during the download (or if the effective file path cannot be
     *                          determined)
     */
    public Tuple<VirtualFile, Boolean> resolveOrLoadChildFromURL(URI url,
                                                                 FetchFromUrlMode mode,
                                                                 Predicate<String> fileExtensionVerifier) {
        return resolveOrLoadChildFromURL(url,
                                         mode,
                                         fileExtensionVerifier,
                                         EnumSet.noneOf(RemoteFileResolver.Options.class));
    }

    /**
     * Attempts to resolve the file from the given URL or performs a download if the file does not exist.
     * <p>
     * Uses the path of the given URL relative to this directory and tries to resolve the child file. If this file
     * does not exist, or has been modified since its last download, a download will be attempted.
     * <p>
     * As a result, the resolved file will be returned (which was either already there or has been downloaded).
     * <p>
     * In order to determine the effective filename/path of the given URL we use the registered
     * {@linkplain RemoteFileResolver remote file resolvers}.
     * <p>
     * This will increment one of the timings (downloaded or download skipped) and also directly report IO
     * errors to the process without spamming the system logs.
     *
     * @param url                   the url to fetch
     * @param mode                  determines under which conditions the data from the given URL should be fetched
     * @param fileExtensionVerifier specifies which extensions are accepted. This should be used to prevent using
     *                              ".php" or the like as effective file name. When in doubt, use
     *                              {@link #notServerSidedScripting(String)} to at least exclude common server-sided
     *                              scripting languages like PHP.
     * @param options               additional options for resolving the file
     * @return the file which has been resolved (and downloaded if necessary) along with a flag which indicates if an
     * update (download) has been performed
     * @throws HandledException in case of an any error during the download (or if the effective file path cannot be
     *                          determined)
     */
    public Tuple<VirtualFile, Boolean> resolveOrLoadChildFromURL(URI url,
                                                                 FetchFromUrlMode mode,
                                                                 Predicate<String> fileExtensionVerifier,
                                                                 Set<RemoteFileResolver.Options> options) {
        Watch watch = Watch.start();

        Tuple<VirtualFile, Boolean> fileAndFlag =
                performResolveOrLoadChildFromURL(url, mode, fileExtensionVerifier, options);

        if (Boolean.TRUE.equals(fileAndFlag.getSecond())) {
            TaskContext.get().addTiming(NLS.get("VirtualFile.fileDownloaded"), watch.elapsedMillis());
        } else {
            TaskContext.get().addTiming(NLS.get("VirtualFile.fileDownloadSkipped"), watch.elapsedMillis());
        }

        return fileAndFlag;
    }

    private Tuple<VirtualFile, Boolean> performResolveOrLoadChildFromURL(URI uri,
                                                                         FetchFromUrlMode mode,
                                                                         Predicate<String> fileExtensionVerifier,
                                                                         Set<RemoteFileResolver.Options> options) {
        try {
            for (RemoteFileResolver resolver : remoteFileResolvers) {
                if (resolver.requiresRequestForPathResolve() && mode == FetchFromUrlMode.NEVER_FETCH) {
                    continue;
                }

                Tuple<VirtualFile, Boolean> fileAndFlag =
                        resolver.resolve(this, uri, mode, fileExtensionVerifier, options);

                if (fileAndFlag != null) {
                    return fileAndFlag;
                }
            }

            if (mode == FetchFromUrlMode.NEVER_FETCH) {
                throw Exceptions.createHandled()
                                .withNLSKey("VirtualFile.loadFromUrl.noValidPathWithoutDownload")
                                .hint(ProcessLog.HINT_MESSAGE_TYPE, MESSAGE_KEY_LOAD_FROM_URL_DISABLED)
                                .hint(ProcessLog.HINT_MESSAGE_COUNT, ProcessLog.MESSAGE_TYPE_COUNT_VERY_LOW)
                                .set("url", uri.toString())
                                .handle();
            }

            throw Exceptions.createHandled()
                            .withNLSKey("VirtualFile.loadFromUrl.noValidPath")
                            .hint(ProcessLog.HINT_MESSAGE_TYPE, MESSAGE_KEY_LOAD_FROM_URL_FAILED)
                            .hint(ProcessLog.HINT_MESSAGE_COUNT, ProcessLog.MESSAGE_TYPE_COUNT_MEDIUM)
                            .set("url", uri.toString())
                            .handle();
        } catch (IOException exception) {
            throw Exceptions.createHandled()
                            .error(exception)
                            .withNLSKey("VirtualFile.downloadFailed")
                            .set("url", uri)
                            .hint(ProcessLog.HINT_MESSAGE_TYPE, MESSAGE_KEY_LOAD_FROM_URL_FAILED)
                            .hint(ProcessLog.HINT_MESSAGE_COUNT, ProcessLog.MESSAGE_TYPE_COUNT_MEDIUM)
                            .handle();
        }
    }

    @ConfigValue("storage.layer3.serverSidedScriptingExtensions")
    private static List<String> serverSidedScriptingExtensions;

    /**
     * Ensures that the given file-extension is present, but doesn't belong to a list of known scripting languages
     * like e.g. PHP.
     *
     * @param fileExtension the file extension to check
     * @return <tt>true</tt> if a file extension is present which doesn't belong to a known server sided scripting
     * language, <tt>false</tt> otherwise.
     */
    public static boolean notServerSidedScripting(String fileExtension) {
        if (Strings.isEmpty(fileExtension)) {
            return false;
        }

        String effectiveExtension = fileExtension.toLowerCase();

        return !serverSidedScriptingExtensions.contains(effectiveExtension);
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

        if ((other instanceof VirtualFile file) && (Objects.equals(file.parent, parent))) {
            return Objects.equals(name, file.name);
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
