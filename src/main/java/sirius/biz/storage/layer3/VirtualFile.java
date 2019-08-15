/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3;

import com.google.common.io.ByteStreams;
import io.netty.handler.codec.http.HttpResponseStatus;
import sirius.biz.storage.util.StorageUtils;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.transformers.Composable;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.kernel.nls.NLS;
import sirius.web.http.MimeHelper;
import sirius.web.http.Response;
import sirius.web.http.WebContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

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

    protected String name;
    protected String description;
    protected VirtualFile parent;
    protected ChildProvider childProvider;
    protected Function<VirtualFile, Long> lastModifiedSupplier;
    protected Function<VirtualFile, Long> sizeSupplier;
    protected Predicate<VirtualFile> directoryFlagSupplier;
    protected Predicate<VirtualFile> existsFlagSupplier;
    protected Predicate<VirtualFile> canCreateChildrenHandler;
    protected Predicate<VirtualFile> canCreateDirectoryHandler;
    protected Function<VirtualFile, Boolean> createDirectoryHandler;
    protected Predicate<VirtualFile> canDeleteHandler;
    protected Function<VirtualFile, Boolean> deleteHandler;
    protected Predicate<VirtualFile> canProvideOutputStream;
    protected Function<VirtualFile, OutputStream> outputStreamSupplier;
    protected Predicate<VirtualFile> canProvideInputStream;
    protected Function<VirtualFile, InputStream> inputStreamSupplier;
    protected BiConsumer<VirtualFile, Response> tunnelHandler = VirtualFile::defaultTunnelHandler;
    protected Predicate<VirtualFile> canMoveHandler;
    protected BiFunction<VirtualFile, VirtualFile, Boolean> moveHandler;
    protected Predicate<VirtualFile> canRenameHandler;
    protected BiFunction<VirtualFile, String, Boolean> renameHandler;

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
        if (Strings.isEmpty(name) || name.contains("/")) {
            throw new IllegalArgumentException("A filename must be filled and must not contain a '/'.");
        }
    }

    /**
     * Determines if the file represents a directory.
     *
     * @return <tt>true</tt> if the file represents a directory, <tt>false</tt> otherwise
     */
    public boolean isDirectory() {
        try {
            if (directoryFlagSupplier == null) {
                return childProvider != null;
            }
            return directoryFlagSupplier.test(this);
        } catch (Exception e) {
            throw handleErrorInCallback(e, "directoryFlagSupplier");
        }
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
     * Returns the name of this file.
     *
     * @return the name of this file
     */
    public String name() {
        return name == null ? "/" : name;
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
            return lastModifiedSupplier == null ? 0 : lastModifiedSupplier.apply(this);
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
            return lastModifiedSupplier == null ?
                   null :
                   Instant.ofEpochMilli(lastModifiedSupplier.apply(this))
                          .atZone(ZoneId.systemDefault())
                          .toLocalDateTime();
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
            return sizeSupplier == null ? 0 : sizeSupplier.apply(this);
        } catch (Exception e) {
            throw handleErrorInCallback(e, "sizeSupplier");
        }
    }

    /**
     * Determines if this file can (probably) be deleted.
     *
     * @return <tt>true</tt> if this file can be delete or <tt>false</tt> otherwise
     */
    public boolean canDelete() {
        try {
            if (canDeleteHandler != null) {
                return canDeleteHandler.test(this);
            }

            return deleteHandler != null;
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
            if (deleteHandler == null) {
                return false;
            }

            return deleteHandler.apply(this);
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
            if (canRenameHandler != null) {
                return canRenameHandler.test(this);
            }

            return renameHandler != null;
        } catch (Exception e) {
            throw handleErrorInCallback(e, "canRenameHandler");
        }
    }

    /**
     * Tries to rename this file.
     *
     * @return <tt>true</tt> if the operation was successful, <tt>false</tt> otherwise
     */
    public boolean tryRename(String newName) {
        try {
            if (renameHandler == null) {
                return false;
            }

            return renameHandler.apply(this, newName);
        } catch (Exception e) {
            throw handleErrorInCallback(e, "renameHandler");
        }
    }

    /**
     * Renames this file.
     *
     * @throws HandledException if the file cannot be renamed
     */
    public void rename(String newName) {
        if (!tryRename(newName)) {
            throw Exceptions.createHandled().withNLSKey("VirtualFile.cannotRename").set("file", path()).handle();
        }
    }

    /**
     * Determines if this file can (probably) be move in an efficient way.
     *
     * @return <tt>true</tt> if this file can be efficiently moved or <tt>false</tt> otherwise
     */
    public boolean canFastMove() {
        try {
            if (canMoveHandler != null) {
                return canMoveHandler.test(this);
            }

            return moveHandler != null;
        } catch (Exception e) {
            throw handleErrorInCallback(e, "canMoveHandler");
        }
    }

    /**
     * Tries to efficiently move the file in the given directory.
     *
     * @param newParent the new parent directory
     * @return <tt>true</tt> if the operation was successful, <tt>false</tt> otherwise
     */
    public boolean tryFastMoveTo(VirtualFile newParent) {
        try {
            if (moveHandler == null) {
                return false;
            }

            return moveHandler.apply(this, newParent);
        } catch (Exception e) {
            throw handleErrorInCallback(e, "moveHandler");
        }
    }

    /**
     * Efficiently moves the file in the given directory.
     *
     * @param newParent the new parent directory
     * @throws HandledException if the file cannot be efficiently moved
     */
    public void fastMoveTo(VirtualFile newParent) {
        if (!tryFastMoveTo(newParent)) {
            throw Exceptions.createHandled().withNLSKey("VirtualFile.cannotMove").set("file", path()).handle();
        }
    }

    /**
     * Determines if this file can bei moved (either via an efficient implementation or by copy+delete).
     *
     * @return <tt>true</tt> if the file can (probably) be moved, <tt>false</tt> otherwise
     */
    public boolean canMove() {
        return canFastMove() || canDelete();
    }

    /**
     * Tries to move this file in the given directory.
     * <p>
     * Note that this might be a long running operation if an efficient implementation cannot be used.
     *
     * @param newParent the new parent directory
     * @return <tt>true</tt> if the operation was successful, <tt>false</tt> otherwise
     */
    public boolean tryMoveTo(VirtualFile newParent) {
        if (tryFastMoveTo(newParent)) {
            return true;
        }

        //TODO implement move manually
        throw new UnsupportedOperationException("Move is currently not implemented");
    }

    /**
     * Determines if the file exists.
     *
     * @return <tt>true</tt> if the file exists, <tt>false</tt> otherwise
     */
    public boolean exists() {
        try {
            if (existsFlagSupplier == null) {
                return false;
            }

            return existsFlagSupplier.test(this);
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
     * Tries to find a child with the given name.
     * <p>
     * Note that the given file can be non-existent.
     *
     * @param name the name of the child to find
     * @return the child wrapped as optional or an empty optional if the given name cannot be resolved into a file.
     */
    public Optional<VirtualFile> findChild(String name) {
        try {
            if (childProvider == null) {
                return Optional.empty();
            }

            return childProvider.findChild(this, name);
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
    public Optional<VirtualFile> tryResolve(String relativePath) {
        if (relativePath != null && relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }

        if (Strings.isEmpty(relativePath)) {
            return Optional.empty();
        }

        Tuple<String, String> nameAndRest = Strings.split(relativePath, "/");
        Optional<VirtualFile> child = findChild(nameAndRest.getFirst());
        if (Strings.isFilled(nameAndRest.getSecond())) {
            return child.flatMap(subChild -> subChild.tryResolve(nameAndRest.getSecond()));
        }

        return child;
    }

    /**
     * Resolves the relative path within this directory.
     *
     * @param relativePath the path to resolve
     * @return the relative path wrapped as optional or an  empty optional if the given relaive path cannot be
     * resolved into a file.
     * @throws HandledException if the given path cannot be resolved
     */
    public VirtualFile resolve(String relativePath) {
        return tryResolve(relativePath).orElseThrow(() -> Exceptions.createHandled()
                                                                    .withNLSKey("VirtualFile.cannotResolveChild")
                                                                    .set("path", path())
                                                                    .set("child", relativePath)
                                                                    .handle());
    }

    /**
     * Determines if children can be created for this file.
     *
     * @return <tt>true</tt> if children can (probably) be created, <tt>false</tt> otherwise
     */
    public boolean canCreateChildren() {
        try {
            if (canCreateChildrenHandler == null) {
                return true;
            }

            return canCreateChildrenHandler.test(this);
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

            if (canCreateDirectoryHandler != null) {
                return canCreateDirectoryHandler.test(this);
            }

            return createDirectoryHandler != null;
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

            if (createDirectoryHandler == null) {
                return false;
            }

            return createDirectoryHandler.apply(this);
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
        try {
            if (canProvideOutputStream != null) {
                return canProvideOutputStream.test(this);
            }

            return outputStreamSupplier != null;
        } catch (Exception e) {
            throw handleErrorInCallback(e, "canProvideOutputStream");
        }
    }

    /**
     * Tries to create an output stream to write to the file.
     *
     * @return an output stream to provide the contents of the child or an empty optional if no output stream can be created
     */
    public Optional<OutputStream> tryCreateOutputStream() {
        try {
            if (outputStreamSupplier == null) {
                return Optional.empty();
            }

            return Optional.ofNullable(outputStreamSupplier.apply(this));
        } catch (Exception e) {
            throw handleErrorInCallback(e, "outputStreamSupplier");
        }
    }

    /**
     * Creates an output stream to write to the file.
     *
     * @return an output stream to provide the contents of the child
     * @throws HandledException if no output stream could be created
     */
    public OutputStream createOutputStream() {
        return tryCreateOutputStream().orElseThrow(() -> Exceptions.createHandled()
                                                                   .withNLSKey("VirtualFile.cannotWrite")
                                                                   .set("file", path())
                                                                   .handle());
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
            if (canProvideInputStream != null) {
                return canProvideInputStream.test(this);
            }

            return inputStreamSupplier != null;
        } catch (Exception e) {
            throw handleErrorInCallback(e, "canProvideInputStream");
        }
    }

    /**
     * Tries to create an input stream to read the contents of the file.
     *
     * @return an input stream to read the contents of the child or an empty optional if no input stream can be created
     */
    public Optional<InputStream> tryCreateInputStream() {
        try {
            if (inputStreamSupplier == null) {
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
     * @return an input stream to read the contents of the child
     * @throws HandledException if no input stream could be created
     */
    public InputStream createInputStream() {
        return tryCreateInputStream().orElseThrow(() -> Exceptions.createHandled()
                                                                  .withNLSKey("VirtualFile.cannotRead")
                                                                  .set("file", path())
                                                                  .handle());
    }

    @Override
    public String toString() {
        return path();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof VirtualFile) {
            if (Objects.equals(((VirtualFile) obj).parent, parent)) {
                return Objects.equals(name, ((VirtualFile) obj).name);
            }
        }

        return false;
    }

    @Override
    public int hashCode() {
        return path().hashCode();
    }

    @Override
    public int compareTo(VirtualFile o) {
        if (o == null) {
            return 1;
        }

        return Objects.compare(path(), o.path(), String::compareTo);
    }

    /**
     * Delivers the contents of this file into the given web context.
     *
     * @param ctx the HTTP request to respond to
     */
    public void deliverTo(WebContext ctx) {
        try {
            if (!exists() || isDirectory()) {
                ctx.respondWith().error(HttpResponseStatus.NOT_FOUND);
                return;
            }

            tunnelHandler.accept(this, ctx.respondWith().download(name()));
        } catch (Exception e) {
            ctx.respondWith()
               .error(HttpResponseStatus.INTERNAL_SERVER_ERROR, handleErrorInCallback(e, "tunnelHandler"));
        }
    }

    /**
     * Delivers the contents of this file as download.
     *
     * @param ctx the HTTP request to respond to
     */
    public void deliverDownloadTo(WebContext ctx) {
        try {
            if (!exists() || isDirectory()) {
                ctx.respondWith().error(HttpResponseStatus.NOT_FOUND);
                return;
            }

            tunnelHandler.accept(this, ctx.respondWith().named(name()));
        } catch (Exception e) {
            ctx.respondWith()
               .error(HttpResponseStatus.INTERNAL_SERVER_ERROR, handleErrorInCallback(e, "tunnelHandler"));
        }
    }

    @SuppressWarnings("UnstableApiUsage")
    private static void defaultTunnelHandler(VirtualFile file, Response response) {
        try (InputStream in = file.createInputStream()) {
            if (in == null) {
                response.error(HttpResponseStatus.NOT_FOUND);
                return;
            }

            try (OutputStream out = response.outputStream(HttpResponseStatus.OK,
                                                          MimeHelper.guessMimeType(file.name()))) {
                ByteStreams.copy(in, out);
            }
        } catch (IOException e) {
            response.error(HttpResponseStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }
}
