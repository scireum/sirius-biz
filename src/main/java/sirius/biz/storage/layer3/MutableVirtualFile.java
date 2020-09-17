/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3;

import sirius.biz.storage.layer1.FileHandle;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.web.http.Response;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;
import java.util.regex.Pattern;

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

    /**
     * Regular Expressions that matches any character that is not allowed for creating a new file.
     * <p>
     * These chars are prohibited as they might be reserved by the file system as indicated here: https://en.wikipedia.org/wiki/Filename#Reserved_characters_and_words
     */
    private static final String REGEX_ILLEGAL_FILE_CHARS = "[/\\\\?%*:|\"<>]";

    protected MutableVirtualFile() {
        super();
    }

    /**
     * Use the static factory methods to obtain a new instance.
     *
     * @see #checkedCreate(VirtualFile, String)
     * @see #safeCreate(VirtualFile, String)
     */
    protected MutableVirtualFile(@Nonnull VirtualFile parent, @Nonnull String name) {
        super(parent, name);
    }

    /**
     * Creates a new file with the given name in the given directory.
     *
     * @param parent the parent of the file
     * @param name   the name of the file
     * @return a new instance of this class
     * @throws IllegalArgumentException if the given name is empty or contains an {@link #REGEX_ILLEGAL_FILE_CHARS illegal} char
     */
    public static MutableVirtualFile checkedCreate(@Nonnull VirtualFile parent, @Nonnull String name) {
        if (Strings.isEmpty(name) || Pattern.compile(REGEX_ILLEGAL_FILE_CHARS).split(name).length > 1) {
            throw new IllegalArgumentException("A filename must be filled and must not contain illegal characters.");
        }
        return new MutableVirtualFile(parent, name);
    }

    /**
     * Creates a new file with the given name in the given directory.
     * <p>
     * Replaces any {@link #REGEX_ILLEGAL_FILE_CHARS illegal} chars in the given String and if the given String is empty, <tt>null</tt> is returned.
     *
     * @param parent the parent of the file
     * @param name   the name of the file
     * @return a new instance of this class
     */
    @Nullable
    public static MutableVirtualFile safeCreate(@Nonnull VirtualFile parent, @Nullable String name) {
        if (Strings.isEmpty(name)) {
            return null;
        }
        return new MutableVirtualFile(parent, name.replaceAll(REGEX_ILLEGAL_FILE_CHARS, "_"));
    }

    /**
     * Creates a new file with the given name in the given directory.
     * <p>
     * This method should only be used if you know what you are doing. In most cases use either {@link #checkedCreate(VirtualFile, String)}
     * or {@link #safeCreate(VirtualFile, String)} to account for {@link #REGEX_ILLEGAL_FILE_CHARS illegal} characters.
     *
     * @param parent the parent of the file
     * @param name   the name of the file
     * @return a new instance of this class
     */
    public static MutableVirtualFile unsafeCreate(@Nonnull VirtualFile parent, @Nonnull String name) {
        return new MutableVirtualFile(parent, name);
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
    public MutableVirtualFile withLastModifiedSupplier(ToLongFunction<VirtualFile> lastModifiedSupplier) {
        this.lastModifiedSupplier = lastModifiedSupplier;
        return this;
    }

    /**
     * Specifies the file size in bytes.
     *
     * @param sizeSupplier the supplier which returns the size in bytes
     * @return the file itself for fluent method calls
     */
    public MutableVirtualFile withSizeSupplier(ToLongFunction<VirtualFile> sizeSupplier) {
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
     * Marks this file as existing file.
     *
     * @return the file itself for fluent method calls
     */
    public MutableVirtualFile markAsExistingFile() {
        return withDirectoryFlagSupplier(CONSTANT_FALSE).withExistsFlagSupplier(CONSTANT_TRUE);
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
    public MutableVirtualFile withCreateDirectoryHandler(Predicate<VirtualFile> createDirectoryHandler) {
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
    public MutableVirtualFile withDeleteHandler(Predicate<VirtualFile> deleteHandler) {
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
     * Determines if a stream can be consumed.
     *
     * @param canConsumeStream the predicate which determines if a given stream can be consumed to provide the new
     *                         contents of this file
     * @return the file itself for fluent method calls
     */
    public MutableVirtualFile withCanConsumeStream(Predicate<VirtualFile> canConsumeStream) {
        this.canConsumeStream = canConsumeStream;
        return this;
    }

    /**
     * Determines if a file can be consumed.
     *
     * @param canConsumeFile the predicate which determines if a file can be consumed to provide the new
     *                       contents of this file
     * @return the file itself for fluent method calls
     */
    public MutableVirtualFile withCanConsumeFile(Predicate<VirtualFile> canConsumeFile) {
        this.canConsumeFile = canConsumeFile;
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
     * Determines if a file handle can be provided for this file.
     *
     * @param canProvideFileHandle the predicate which determines if a file handle can be created for the
     *                             given file
     * @return the file itself for fluent method calls
     */
    public MutableVirtualFile withCanProvideFileHandle(Predicate<VirtualFile> canProvideFileHandle) {
        this.canProvideFileHandle = canProvideFileHandle;
        return this;
    }

    /**
     * Permits to obtain the contents of this file as file handle.
     *
     * @param fileHandleSupplier the handler which provides a file handle for the contents of this file
     * @return the file itself for fluent method calls
     */
    public MutableVirtualFile withFileHandleSupplier(Function<VirtualFile, FileHandle> fileHandleSupplier) {
        this.fileHandleSupplier = fileHandleSupplier;
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
     * Provides a handler which consumes the given stream to provide the new contents of this file.
     *
     * @param consumeStreamHandler the handler which consumes a given stream and its known length to provide the new
     *                             contents if this file
     * @return the file itself for fluent method calls
     */
    public MutableVirtualFile withConsumeStreamHandler(BiConsumer<VirtualFile, Tuple<InputStream, Long>> consumeStreamHandler) {
        this.consumeStreamHandler = consumeStreamHandler;
        return this;
    }

    /**
     * Provides a handler which consumes the given file to provide the new contents of this file.
     *
     * @param consumeFileHandler the handler which consumes a given file to provide the new
     *                           contents if this file
     * @return the file itself for fluent method calls
     */
    public MutableVirtualFile withConsumeFileHandler(BiConsumer<VirtualFile, File> consumeFileHandler) {
        this.consumeFileHandler = consumeFileHandler;
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
     * @param canMoveHandler the predicate which determines if there is an efficient way of moving this file into the
     *                       new parent
     * @return the file itself for fluent method calls
     */
    public MutableVirtualFile withCanFastMoveHandler(BiPredicate<VirtualFile, VirtualFile> canMoveHandler) {
        this.canFastMoveHandler = canMoveHandler;
        return this;
    }

    /**
     * Provides a handler which permits to efficiently move a file.
     *
     * @param moveHandler the handler which efficiently moves a file into the given directory
     * @return the file itself for fluent method calls
     */
    public MutableVirtualFile withFastMoveHandler(BiPredicate<VirtualFile, VirtualFile> moveHandler) {
        this.fastMoveHandler = moveHandler;
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
    public MutableVirtualFile withRenameHandler(BiPredicate<VirtualFile, String> renameHandler) {
        this.renameHandler = renameHandler;
        return this;
    }
}
