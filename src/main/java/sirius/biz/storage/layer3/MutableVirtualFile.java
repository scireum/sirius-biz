/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3;

import sirius.web.http.Response;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Represents the mutable version of a {@link VirtualFile} which can be used to provide all necessarry callbacks.
 */
public class MutableVirtualFile extends VirtualFile {

    /**
     * Provides a prediacte which is always <tt>true</tt>.
     */
    public static final Predicate<VirtualFile> CONSTANT_TRUE = ignored -> true;

    /**
     * Provides a prediacte which is always <tt>false</tt>.
     */
    public static final Predicate<VirtualFile> CONSTANT_FALSE = ignored -> false;

    protected MutableVirtualFile() {
        super();
    }

    /**
     * Creates a new file with the given name.
     *
     * @param parent the parent of the file
     * @param name   the name of the file
     */
    public MutableVirtualFile(@Nonnull VirtualFile parent, @Nonnull String name) {
        super(parent, name);
    }

    /**
     * Provides an additional description which can be shown in the web based UI.
     *
     * @param description the description to show
     *                    (this will be {@link sirius.kernel.nls.NLS#smartGet(String) smart translated}).
     * @return the file itself for fluent method calls
     */
    public MutableVirtualFile withDescription(String description) {
        this.description = description;
        return this;
    }

    /**
     * Determines if children can be created for this file.
     *
     * @param canCreateChildrenHandler the predicate used to determine if children can be created for the given file
     * @return the file itself for fluent method calls
     */
    public MutableVirtualFile withCanCreateChildren(Predicate<VirtualFile> canCreateChildrenHandler) {
        this.canCreateChildrenHandler = canCreateChildrenHandler;
        return this;
    }

    /**
     * Determines if file is generally considered readonly.
     * <p>
     * If a file is marked as readonly, all mutators like {@link #canDelete()} or {@link #canConsumeFile} etc. will
     * automatically return false.
     *
     * @param readonlyHandler the predicate used to determine if this file is considered readonly
     * @return the file itself for fluent method calls
     */
    public MutableVirtualFile withReadonlyHandler(Predicate<VirtualFile> readonlyHandler) {
        this.readonlyHandler = readonlyHandler;
        return this;
    }

    /**
     * Used to retrieve all child files of a directory.
     *
     * @param childProvider the provider which supplies all children for a given search query
     * @return the file itself for fluent method calls
     */
    public MutableVirtualFile withChildren(ChildProvider childProvider) {
        this.childProvider = childProvider;
        return this;
    }

    /**
     * Specifies the timestamp which represents the last modification.
     *
     * @param lastModifiedSupplier the supplier which computes the timestamp of the last modification
     * @return the file itself for fluent method calls
     * @see File#lastModified()
     */
    public MutableVirtualFile withLastModifiedSupplier(Function<VirtualFile, Long> lastModifiedSupplier) {
        this.lastModifiedSupplier = lastModifiedSupplier;
        return this;
    }

    /**
     * Specifies the file size in bytes.
     *
     * @param sizeSupplier the supplier which returns the size in bytes
     * @return the file itself for fluent method calls
     */
    public MutableVirtualFile withSizeSupplier(Function<VirtualFile, Long> sizeSupplier) {
        this.sizeSupplier = sizeSupplier;
        return this;
    }

    /**
     * Determines if the file is a directory.
     *
     * @param directoryFlagSupplier the supplier which determines if the given file is a directory
     * @return the file itself for fluent method calls
     */
    public MutableVirtualFile withDirectoryFlagSupplier(Predicate<VirtualFile> directoryFlagSupplier) {
        this.directoryFlagSupplier = directoryFlagSupplier;
        return this;
    }

    /**
     * Determines if the file does exist.
     *
     * @param existsFlagSupplier the supplier which determines if the given file exists
     * @return the file itself for fluent method calls
     */
    public MutableVirtualFile withExistsFlagSupplier(Predicate<VirtualFile> existsFlagSupplier) {
        this.existsFlagSupplier = existsFlagSupplier;
        return this;
    }

    /**
     * Marks this file as existing directory.
     *
     * @return the file itself for fluent method calls
     */
    public MutableVirtualFile markAsExistingDirectory() {
        return withDirectoryFlagSupplier(CONSTANT_TRUE).withExistsFlagSupplier(CONSTANT_TRUE);
    }

    /**
     * Determines if a file can be created as directory.
     *
     * @param canCreateDirectoryHandler the prediacte which determines if the given file can be created as directory
     * @return the file itself for fluent method calls
     */
    public MutableVirtualFile withCanCreateDirectoryHandler(Predicate<VirtualFile> canCreateDirectoryHandler) {
        this.canCreateDirectoryHandler = canCreateDirectoryHandler;
        return this;
    }

    /**
     * Permits to create the file as directory.
     *
     * @param createDirectoryHandler the handler to create the given file as directory
     * @return the file itself for fluent method calls
     */
    public MutableVirtualFile withCreateDirectoryHandler(Function<VirtualFile, Boolean> createDirectoryHandler) {
        this.createDirectoryHandler = createDirectoryHandler;
        return this;
    }

    /**
     * Determines if a file can be deleted.
     *
     * @param canDeleteHandler the predicate which determines if the given file can be deleted
     * @return the file itself for fluent method calls
     */
    public MutableVirtualFile withCanDeleteHandler(Predicate<VirtualFile> canDeleteHandler) {
        this.canDeleteHandler = canDeleteHandler;
        return this;
    }

    /**
     * Permits to delete a file.
     *
     * @param deleteHandler the handler to delete the given file
     * @return the file itself for fluent method calls
     */
    public MutableVirtualFile withDeleteHandler(Function<VirtualFile, Boolean> deleteHandler) {
        this.deleteHandler = deleteHandler;
        return this;
    }

    /**
     * Determines if an output stream can be created.
     *
     * @param canProvideOutputStream the predicate which determines if an output stream can be created for the
     *                               given file
     * @return the file itself for fluent method calls
     */
    public MutableVirtualFile withCanProvideOutputStream(Predicate<VirtualFile> canProvideOutputStream) {
        this.canProvideOutputStream = canProvideOutputStream;
        return this;
    }

    /**
     * Determines if an input stream can be created.
     *
     * @param canProvideInputStream the predicate which determines if an input stream can be created for the
     *                              given file
     * @return the file itself for fluent method calls
     */
    public MutableVirtualFile withCanProvideInputStream(Predicate<VirtualFile> canProvideInputStream) {
        this.canProvideInputStream = canProvideInputStream;
        return this;
    }

    /**
     * Permits to read the contents of a file.
     *
     * @param inputStreamSupplier the handler which provides an input stream of the files contents.
     * @return the file itself for fluent method calls
     */
    public MutableVirtualFile withInputStreamSupplier(Function<VirtualFile, InputStream> inputStreamSupplier) {
        this.inputStreamSupplier = inputStreamSupplier;
        return this;
    }

    /**
     * Permits to write the contents of a file.
     *
     * @param outputStreamSupplier the handler which provides an output stream to write into the file.
     * @return the file itself for fluent method calls
     */
    public MutableVirtualFile withOutputStreamSupplier(Function<VirtualFile, OutputStream> outputStreamSupplier) {
        this.outputStreamSupplier = outputStreamSupplier;
        return this;
    }

    /**
     * Provides an efficient way of sending the file contents into the given HTTP response.
     *
     * @param tunnelHandler the handler which sends the contents of the given file into the given response
     * @return the file itself for fluent method calls
     */
    public MutableVirtualFile withCustomTunnelHandler(BiConsumer<VirtualFile, Response> tunnelHandler) {
        this.tunnelHandler = tunnelHandler;
        return this;
    }

    /**
     * Determines if there is an efficient way of moving this file.
     *
     * @param canMoveHandler the predicate which determines if there is an efficient way of moving this file into a
     *                       new parent
     * @return the file itself for fluent method calls
     */
    public MutableVirtualFile withCanMoveHandler(Predicate<VirtualFile> canMoveHandler) {
        this.canMoveHandler = canMoveHandler;
        return this;
    }

    /**
     * Provides a handler which permits to efficiently move a file.
     *
     * @param moveHandler the handler which efficiently moves a file into another directory
     * @return the file itself for fluent method calls
     */
    public MutableVirtualFile withMoveHandler(BiFunction<VirtualFile, VirtualFile, Boolean> moveHandler) {
        this.moveHandler = moveHandler;
        return this;
    }

    /**
     * Determines if a file can be renamed.
     *
     * @param canRenameHandler the predicate which determines if the given file can be renamed
     * @return the file itself for fluent method calls
     */
    public MutableVirtualFile withCanRenameHandler(Predicate<VirtualFile> canRenameHandler) {
        this.canRenameHandler = canRenameHandler;
        return this;
    }

    /**
     * Renames a file.
     *
     * @param renameHandler the handler which renames the given file
     * @return the file itself for fluent method calls
     */
    public MutableVirtualFile withRenameHandler(BiFunction<VirtualFile, String, Boolean> renameHandler) {
        this.renameHandler = renameHandler;
        return this;
    }
}
