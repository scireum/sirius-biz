/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.vfs;

import sirius.kernel.commons.Strings;
import sirius.kernel.commons.ValueHolder;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Represents a file or directory in the VirtualFileSystem.
 * <p>
 * The way this file can be modified, can be controlled by providing the appropriate callbacks. If no appropriate
 * handler is given, the operation is blocked for that file. Therefore, by default, a file is unmodifyable unless
 * some handlers are provided.
 *
 * @deprecated Replaced by {@link sirius.biz.storage.layer3.VirtualFile}
 */
@Deprecated
public class VirtualFile {

    private final String name;
    private VirtualFile parent;
    private BiConsumer<VirtualFile, Consumer<VirtualFile>> childProvider;
    private long lastModified;
    private long size;
    private Function<String, Boolean> createDirectoryHandler;
    private Function<String, OutputStream> createFileHandler;
    private Supplier<Boolean> deleteHandler;
    private Supplier<OutputStream> outputStreamSupplier;
    private Supplier<InputStream> inputStreamSupplier;

    private VirtualFile(String name) {
        this.name = name;
    }

    /**
     * Creates a new file with the given name.
     *
     * @param parent the parent of the file
     * @param name   the name of the file
     */
    public VirtualFile(@Nonnull VirtualFile parent, @Nonnull String name) {
        this.parent = parent;
        this.name = name;
    }

    /**
     * Creates a root node, which represents "/".
     *
     * @return now file, which can be used as root node for a virtual file system
     */
    public static VirtualFile createRootNode() {
        return new VirtualFile("/");
    }

    /**
     * Used to retrieve all child files of a directory.
     *
     * @param childProvider the provider which supplies all children to a consumer
     * @return the file itself for fluent method calls
     */
    public VirtualFile withChildren(BiConsumer<VirtualFile, Consumer<VirtualFile>> childProvider) {
        this.childProvider = childProvider;
        return this;
    }

    /**
     * Specifies the timestamp which represents the last modification.
     *
     * @param lastModified the timestamp of the last modification
     * @return the file itself for fluent method calls
     * @see File#lastModified()
     */
    public VirtualFile withLastModified(long lastModified) {
        this.lastModified = lastModified;
        return this;
    }

    /**
     * Specifies the file size in bytes.
     *
     * @param size the size in bytes
     * @return the file itself for fluent method calls
     */
    public VirtualFile withSize(long size) {
        this.size = size;
        return this;
    }

    /**
     * Permits to create sub directories by invoking the given handler.
     *
     * @param createDirectoryHandler the handler to create a sub directory with the given name
     * @return the file itself for fluent method calls
     */
    public VirtualFile withCreateDirectoryHandler(Function<String, Boolean> createDirectoryHandler) {
        this.createDirectoryHandler = createDirectoryHandler;
        return this;
    }

    /**
     * Permits to create a child file with the given name and contents.
     *
     * @param createFileHandler the handler to create a child file
     * @return the file itself for fluent method calls
     */
    public VirtualFile withCreateFileHandler(Function<String, OutputStream> createFileHandler) {
        this.createFileHandler = createFileHandler;
        return this;
    }

    /**
     * Permits to delete a file.
     *
     * @param deleteHandler the handler to delete the file
     * @return the file itself for fluent method calls
     */
    public VirtualFile withDeleteHandler(Supplier<Boolean> deleteHandler) {
        this.deleteHandler = deleteHandler;
        return this;
    }

    /**
     * Permits to read the contents of a file.
     *
     * @param inputStreamSupplier the handler which provides an input stream of the files contents.
     * @return the file itself for fluent method calls
     */
    public VirtualFile withInputStreamSupplier(Supplier<InputStream> inputStreamSupplier) {
        this.inputStreamSupplier = inputStreamSupplier;
        return this;
    }

    /**
     * Permits to write the contents of a file.
     *
     * @param outputStreamSupplier the handler which provides an output stream to write into the file.
     * @return the file itself for fluent method calls
     */
    public VirtualFile withOutputStreamSupplier(Supplier<OutputStream> outputStreamSupplier) {
        this.outputStreamSupplier = outputStreamSupplier;
        return this;
    }

    /**
     * Determines if the file represents a directory.
     *
     * @return <tt>true</tt> if the file represents a directory, <tt>false</tt> otherwise
     */
    public boolean isDirectory() {
        return childProvider != null;
    }

    /**
     * Returns the parent file of this file.
     *
     * @return the parent of this file
     */
    public VirtualFile getParent() {
        return parent;
    }

    /**
     * Returns the absolute path of this file within the virtual file system.
     *
     * @return the absolute path of this file
     */
    public String getPath() {
        if (parent != null) {
            return parent.getPath() + "/" + name;
        }

        return name == null ? "" : name;
    }

    /**
     * Returns the name of this file.
     *
     * @return the name of this file
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the last modification timestamp.
     *
     * @return the last modification as in {@link File#lastModified()}
     */
    public long getLastModified() {
        return lastModified;
    }

    /**
     * Returns the size of this file.
     *
     * @return the size in bytes
     */
    public long getSize() {
        return size;
    }

    /**
     * Tries to delete this file.
     *
     * @return <tt>true</tt> if the operation was successful, <tt>false</tt> otherwise
     */
    public boolean delete() {
        if (deleteHandler == null) {
            return false;
        }

        return deleteHandler.get();
    }

    /**
     * Enumerates all children of this file.
     *
     * @param childCollector the consumer collecting all children of this file
     */
    public void enumerateChildren(Consumer<VirtualFile> childCollector) {
        if (childProvider != null) {
            childProvider.accept(this, childCollector);
        }
    }

    /**
     * Tries to find a child with the given name.
     *
     * @param name the name of the child to find
     * @return the child wrapped as optional or an empty optional if no child with the given name was found
     */
    public Optional<VirtualFile> findChild(String name) {
        ValueHolder<VirtualFile> childHolder = new ValueHolder<>(null);
        if (childProvider != null) {
            childProvider.accept(this, c -> {
                if (Strings.areEqual(name, c.getName())) {
                    childHolder.set(c);
                }
            });
        }

        return Optional.ofNullable(childHolder.get());
    }

    /**
     * Tries to create a subdirectory with the given name.
     *
     * @param name the name of the subdirectory to create
     * @return <tt>true</tt> if the operation was successful, <tt>false</tt> otherwise
     */
    public boolean createChildDirectory(String name) {
        if (createDirectoryHandler == null) {
            return false;
        }

        return createDirectoryHandler.apply(name);
    }

    /**
     * Creates a child file with the given name.
     *
     * @param childName the name of the child.
     * @return an output stream to provide the contents of the child
     */
    public OutputStream createChildFile(String childName) {
        if (createFileHandler == null) {
            return null;
        }

        return createFileHandler.apply(childName);
    }

    /**
     * Creates an output stream to write to the file.
     *
     * @return an output stream to provide the contents of the child
     */
    public OutputStream createOutputStream() {
        if (outputStreamSupplier == null) {
            return null;
        }

        return outputStreamSupplier.get();
    }

    /**
     * Determines if the file is readable.
     *
     * @return <tt>true</tt> if the file is readable, <tt>false</tt> otherwise
     */
    public boolean isReadable() {
        return inputStreamSupplier != null;
    }

    /**
     * Determines if the file is writeable.
     *
     * @return <tt>true</tt> if the file is writeable, <tt>false</tt> otherwise
     */
    public boolean isWriteable() {
        return outputStreamSupplier != null;
    }

    /**
     * Creates an input stream to read the contents of the file.
     *
     * @return an input stream to read the contents of the file
     */
    public InputStream createInputStream() {
        if (inputStreamSupplier == null) {
            return null;
        }

        return inputStreamSupplier.get();
    }

    @Override
    public String toString() {
        return getPath();
    }
}
