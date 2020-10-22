/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2;

import io.netty.handler.codec.http.HttpResponseStatus;
import sirius.biz.storage.layer1.FileHandle;
import sirius.biz.storage.layer3.ChildProvider;
import sirius.biz.storage.layer3.FileSearch;
import sirius.biz.storage.layer3.MutableVirtualFile;
import sirius.biz.storage.layer3.VFSRoot;
import sirius.biz.storage.layer3.VirtualFile;
import sirius.biz.tenants.Tenants;
import sirius.kernel.commons.Files;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.http.Response;
import sirius.web.security.ScopeInfo;
import sirius.web.security.UserContext;

import javax.annotation.Nullable;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Provides the adapter between the layer 2 (blob storage) and the layer 3 (browsable virtual file system).
 * <p>
 * Note that this is also accessible via an {@link Part} annotation as the helper methods
 * {@link #wrapBlob(VirtualFile, Blob, boolean)} and {@link #wrapDirectory(VirtualFile, Directory, boolean)} might be
 * used by custom VFS roots which also access blobs under the hoods.
 */
@Register(classes = {VFSRoot.class, L3Uplink.class})
public class L3Uplink implements VFSRoot {

    @Part
    private BlobStorage storage;

    @Part
    private Tenants<?, ?, ?> tenants;

    /**
     * Represents a non-existent file or directory which might be created by
     * {@link DirectoryChildProvider#findChild(VirtualFile, String)}.
     */
    static class Placeholder {
        private final VirtualFile parent;
        private final String name;

        Placeholder(VirtualFile parent, String name) {
            this.parent = parent;
            this.name = name;
        }

        VirtualFile getParent() {
            return parent;
        }

        String getName() {
            return name;
        }

        /**
         * Ensures that the parent is a proper directory.
         * <p>
         * Due to the architecture of the VFS, even the parent might be a {@link Placeholder} which has to be created
         * first.
         *
         * @return the parent as "real" {@link Directory}
         */
        Directory getParentAsExistingDirectory() {
            if (!parent.exists()) {
                parent.createAsDirectory();
            }

            return parent.as(Directory.class);
        }

        /**
         * Creates a new directory for this placeholder.
         *
         * @return the newly created directory
         */
        Directory createAsDirectory() {
            return getParentAsExistingDirectory().findOrCreateChildDirectory(name);
        }

        /**
         * Creates a new blob for this placeholder.
         *
         * @return the newly created blob
         */
        Blob createAsBlob() {
            return getParentAsExistingDirectory().findOrCreateChildBlob(name);
        }
    }

    /**
     * As the child provider is stateless, we can use a shared instance.
     */
    protected final DirectoryChildProvider directoryChildProvider = new DirectoryChildProvider();

    /**
     * Responsible for resolving children of a {@link Directory} and transforming them into
     * {@link VirtualFile virtual files}.
     */
    class DirectoryChildProvider implements ChildProvider {

        /**
         * Creates a placeholder for the given parent and name.
         *
         * @param parentDirectory the directory which (if present) is used to determine if the underlying storage space
         *                        is readonly. In this case an empty optional is returned
         * @param parent          the parent to pass on
         * @param name            the name of the placeholder
         * @return a placeholder representing a non existent file or directory
         */
        @Nullable
        protected VirtualFile createPlaceholder(@Nullable Directory parentDirectory, VirtualFile parent, String name) {
            if (parentDirectory != null && parentDirectory.getStorageSpace().isReadonly()) {
                return null;
            }

            MutableVirtualFile file = MutableVirtualFile.checkedCreate(parent, name);
            file.attach(new Placeholder(parent, name));
            attachHandlers(file, false);

            return file;
        }

        @Override
        @Nullable
        public VirtualFile findChild(VirtualFile parent, String name) {
            Optional<Directory> parentDirectory = parent.tryAs(Directory.class);
            if (parentDirectory.isPresent()) {
                return findChildInDirectory(parent, parentDirectory.get(), name);
            }

            if (parent.is(Placeholder.class)) {
                // If the parent is a placeholder then we have already ensured that the storage space
                // isn't readonly so therefore we can skip this check and pass null als parent directory.
                return createPlaceholder(null, parent, name);
            }

            return null;
        }

        private VirtualFile findChildInDirectory(VirtualFile parent, Directory parentDir, String name) {
            // If there is a file extension, this is most probably a file / blob. Therefore we first
            // try to resolve it as such and then fallback to resolving as directory...
            if (Strings.isFilled(Files.getFileExtension(name))) {
                return priorizedLookup(() -> parentDir.findChildBlob(name)
                                                      .map(blob -> wrapBlob(parent, blob, false))
                                                      .orElse(null),
                                       () -> parentDir.findChildDirectory(name)
                                                      .map(directory -> wrapDirectory(parent, directory, false))
                                                      .orElse(null),
                                       () -> createPlaceholder(parentDir, parent, name));
            } else {
                //...if there is no file extension we reverse the lookup order
                return priorizedLookup(() -> parentDir.findChildDirectory(name)
                                                      .map(directory -> wrapDirectory(parent, directory, false))
                                                      .orElse(null),
                                       () -> parentDir.findChildBlob(name)
                                                      .map(blob -> wrapBlob(parent, blob, false))
                                                      .orElse(null),
                                       () -> createPlaceholder(parentDir, parent, name));
            }
        }

        /**
         * Uses the given lookup and returns the first non empty optional.
         *
         * @param lookups the lookup to apply
         * @return the first non empty optional produced by the lookups or an empty optional if all lokkups failed
         */
        @SafeVarargs
        private final VirtualFile priorizedLookup(Supplier<VirtualFile>... lookups) {
            for (Supplier<VirtualFile> lookup : lookups) {
                VirtualFile result = lookup.get();
                if (result != null) {
                    return result;
                }
            }

            return null;
        }

        @Override
        public void enumerate(VirtualFile parent, FileSearch search) {
            Optional<Directory> parentDirectory = parent.tryAs(Directory.class);
            if (parentDirectory.isPresent()) {
                parentDirectory.get()
                               .listChildDirectories(search.getPrefixFilter().orElse(null),
                                                     search.getMaxRemainingItems().orElse(0),
                                                     directory -> search.processResult(wrapDirectory(parent,
                                                                                                     directory,
                                                                                                     false)));
                if (!search.isOnlyDirectories()) {
                    parentDirectory.get()
                                   .listChildBlobs(search.getPrefixFilter().orElse(null),
                                                   search.getFileExtensionFilters(),
                                                   search.getMaxRemainingItems().orElse(0),
                                                   blob -> search.processResult(wrapBlob(parent, blob, false)));
                }
            }
        }
    }

    /**
     * Wraps the given directory into a {@link MutableVirtualFile}.
     *
     * @param parent        the parent file to use
     * @param directory     the directory to wrap
     * @param forceReadonly determines if the directory should be forcefully set to readonly (independent of
     *                      {@link BlobStorageSpace#isReadonly()}. Note that setting this to <tt>false</tt> will not
     *                      make the directory writable if <tt>BlobStorageSpace#isReadonly()</tt> returns <tt>true</tt>
     * @return the resulting mutable virtual file
     */
    public MutableVirtualFile wrapDirectory(VirtualFile parent, Directory directory, boolean forceReadonly) {
        MutableVirtualFile file = MutableVirtualFile.checkedCreate(parent, directory.getName());
        file.attach(Directory.class, directory);
        attachHandlers(file, forceReadonly);

        return file;
    }

    /**
     * Wraps the given blob into a {@link MutableVirtualFile}.
     *
     * @param parent        the parent file to use
     * @param blob          the blob to wrap
     * @param forceReadonly determines if the blob should be forcefully set to readonly (independent of
     *                      {@link BlobStorageSpace#isReadonly()}. Note that setting this to <tt>false</tt> will not
     *                      make the blob writable if <tt>BlobStorageSpace#isReadonly()</tt> returns <tt>true</tt>
     * @return the resulting mutable virtual file
     */
    public MutableVirtualFile wrapBlob(VirtualFile parent, Blob blob, boolean forceReadonly) {
        MutableVirtualFile file = MutableVirtualFile.checkedCreate(parent, blob.getFilename());
        file.attach(Blob.class, blob);
        attachHandlers(file, forceReadonly);

        return file;
    }

    /**
     * Maps the requested child names (which will be top-level directories) to
     * {@link BasicBlobStorageSpace#isBrowsable() browsable} storage spaces.
     *
     * @param parent the directory to resolve the child in
     * @param name   the name of the child to resolve
     * @return a virtual file representing the storage space with the given name or an empty optional if none was found
     */
    @Override
    @Nullable
    public VirtualFile findChild(VirtualFile parent, String name) {
        if (storage == null || !storage.isKnown(name)) {
            return null;
        }

        if (!isDefaultScope()) {
            return null;
        }

        BlobStorageSpace space = storage.getSpace(name);
        if (!space.isBrowsable()) {
            return null;
        }

        return wrapDirectory(parent, space.getRoot(tenants.getRequiredTenant().getIdAsString()), false);
    }

    protected boolean isDefaultScope() {
        return ScopeInfo.DEFAULT_SCOPE.equals(UserContext.getCurrentScope());
    }

    /**
     * Lists all {@link BasicBlobStorageSpace#isBrowsable() browsable} storage spaces.
     *
     * @param parent the directory to enumerate
     * @param search the search criteria and result collector to use
     */
    @Override
    public void enumerate(VirtualFile parent, FileSearch search) {
        if (storage == null) {
            return;
        }

        if (!isDefaultScope()) {
            return;
        }

        storage.getSpaces().filter(BlobStorageSpace::isBrowsable).map(space -> {
            Directory directory = space.getRoot(tenants.getRequiredTenant().getIdAsString());
            MutableVirtualFile wrappedDirectory = wrapDirectory(parent, directory, false);
            wrappedDirectory.withDescription(space.getDescription());
            return wrappedDirectory;
        }).forEach(search::processResult);
    }

    @Override
    public int getPriority() {
        return DEFAULT_PRIORITY;
    }

    private void attachHandlers(MutableVirtualFile file, boolean readonly) {
        file.withChildren(directoryChildProvider);
        file.withCreateDirectoryHandler(this::createDirectoryHandler);
        file.withDirectoryFlagSupplier(this::directoryFlagSupplier);
        file.withExistsFlagSupplier(this::existsFlagSupplier);
        file.withLastModifiedSupplier(this::lastModifiedSupplier);
        file.withSizeSupplier(this::sizeSupplier);
        file.withCanProvideInputStream(this::isReadable);
        file.withInputStreamSupplier(this::inputStreamSupplier);
        file.withCanProvideFileHandle(this::isReadable);
        file.withFileHandleSupplier(this::fileHandleSupplier);
        file.withCustomTunnelHandler(this::tunnelHandler);

        if (readonly) {
            file.withCanCreateChildren(MutableVirtualFile.CONSTANT_FALSE);
            file.withCanCreateDirectoryHandler(MutableVirtualFile.CONSTANT_FALSE);
            file.withCanDeleteHandler(MutableVirtualFile.CONSTANT_FALSE);
            file.withCanRenameHandler(MutableVirtualFile.CONSTANT_FALSE);
            file.withCanFastMoveHandler((ignoredSource, ignoredDestination) -> false);
            file.withCanProvideOutputStream(MutableVirtualFile.CONSTANT_FALSE);
            file.withCanConsumeStream(MutableVirtualFile.CONSTANT_FALSE);
            file.withCanConsumeFile(MutableVirtualFile.CONSTANT_FALSE);
        } else {
            file.withCanCreateDirectoryHandler(this::canCreateDirectoryHandler);
            file.withCanCreateChildren(this::canCreateChildren);
            file.withCanDeleteHandler(this::isMutable);
            file.withDeleteHandler(this::deleteHandler);
            file.withCanRenameHandler(this::isMutable);
            file.withRenameHandler(this::renameHandler);
            file.withCanFastMoveHandler(this::canFastMoveHandler);
            file.withFastMoveHandler(this::fastMoveHandler);
            file.withCanProvideOutputStream(this::isWriteable);
            file.withOutputStreamSupplier(this::outputStreamSupplier);
            file.withCanConsumeStream(this::isWriteable);
            file.withConsumeStreamHandler(this::consumeStreamHandler);
            file.withCanConsumeFile(this::isWriteable);
            file.withConsumeFileHandler(this::consumeFileHandler);
        }
    }

    private long sizeSupplier(VirtualFile file) {
        return file.tryAs(Blob.class).map(Blob::getSize).orElse(0L);
    }

    private long lastModifiedSupplier(VirtualFile file) {
        return file.tryAs(Blob.class)
                   .map(blob -> blob.getLastModified().toInstant(ZoneOffset.UTC).toEpochMilli())
                   .orElse(0L);
    }

    private boolean canCreateChildren(VirtualFile file) {
        return file.tryAs(Directory.class).map(dir -> !dir.getStorageSpace().isReadonly()).orElse(false);
    }

    private boolean isMutable(Blob blob) {
        return !blob.getStorageSpace().isReadonly();
    }

    private boolean isMutable(Directory directory) {
        return !directory.isRoot() && !directory.getStorageSpace().isReadonly();
    }

    private boolean isMutable(VirtualFile file) {
        return file.tryAs(Directory.class).map(this::isMutable).orElse(false) || file.tryAs(Blob.class)
                                                                                     .map(this::isMutable)
                                                                                     .orElse(false);
    }

    private boolean renameHandler(VirtualFile file, String newName) {
        Optional<Directory> directory = file.tryAs(Directory.class);
        if (directory.isPresent()) {
            directory.get().rename(newName);
            return true;
        }

        Optional<Blob> blob = file.tryAs(Blob.class);
        if (blob.isPresent()) {
            blob.get().rename(newName);
            return true;
        }

        return false;
    }

    private boolean deleteHandler(VirtualFile file) {
        Optional<Directory> directory = file.tryAs(Directory.class);
        if (directory.isPresent()) {
            directory.get().delete();
            return true;
        }

        Optional<Blob> blob = file.tryAs(Blob.class);
        if (blob.isPresent()) {
            blob.get().delete();
            return true;
        }

        return false;
    }

    private boolean canFastMoveHandler(VirtualFile file, VirtualFile newParent) {
        Optional<Directory> destDirectory = newParent.tryAs(Directory.class);
        if (!destDirectory.isPresent()) {
            return false;
        }

        Optional<Directory> directory = file.tryAs(Directory.class);
        if (directory.isPresent()) {
            return Strings.areEqual(directory.get().getSpaceName(), destDirectory.get().getSpaceName());
        }

        Optional<Blob> blob = file.tryAs(Blob.class);
        return blob.filter(value -> Strings.areEqual(value.getSpaceName(), destDirectory.get().getSpaceName()))
                   .isPresent();
    }

    private boolean fastMoveHandler(VirtualFile file, VirtualFile newParent) {
        Optional<Directory> directory = file.tryAs(Directory.class);
        if (directory.isPresent()) {
            directory.get().move(newParent.as(Directory.class));
            return true;
        }

        Optional<Blob> blob = file.tryAs(Blob.class);
        if (blob.isPresent()) {
            blob.get().move(newParent.as(Directory.class));
            return true;
        }

        return false;
    }

    private boolean canCreateDirectoryHandler(VirtualFile file) {
        return !file.is(Blob.class) && !file.is(Directory.class) && file.is(Placeholder.class);
    }

    private boolean createDirectoryHandler(VirtualFile file) {
        Placeholder placeholder = file.as(Placeholder.class);
        file.attach(Directory.class, placeholder.createAsDirectory());
        return true;
    }

    private boolean existsFlagSupplier(VirtualFile file) {
        return file.is(Directory.class) || file.is(Blob.class);
    }

    private boolean directoryFlagSupplier(VirtualFile file) {
        return file.is(Directory.class);
    }

    private boolean isReadable(VirtualFile file) {
        return file.is(Blob.class);
    }

    private InputStream inputStreamSupplier(VirtualFile file) {
        return file.as(Blob.class).createInputStream();
    }

    private FileHandle fileHandleSupplier(VirtualFile file) {
        return file.as(Blob.class).download().orElse(null);
    }

    private void tunnelHandler(VirtualFile file, Response response) {
        Optional<Blob> blob = file.tryAs(Blob.class);
        if (blob.isPresent()) {
            blob.get().deliver(response);
        } else {
            response.error(HttpResponseStatus.NOT_FOUND);
        }
    }

    private boolean isWriteable(VirtualFile file) {
        return file.is(Placeholder.class) || file.tryAs(Blob.class).map(this::isMutable).orElse(false);
    }

    private Blob findOrCreateBlob(VirtualFile file) {
        Optional<Blob> blob = file.tryAs(Blob.class);
        if (blob.isPresent()) {
            return blob.get();
        }

        Blob newBlob = file.as(Placeholder.class).createAsBlob();
        file.attach(Blob.class, newBlob);
        return newBlob;
    }

    private OutputStream outputStreamSupplier(VirtualFile file) {
        return findOrCreateBlob(file).createOutputStream(null);
    }

    private void consumeStreamHandler(VirtualFile file, Tuple<InputStream, Long> inputStreamDescription) {
        findOrCreateBlob(file).updateContent(null,
                                             inputStreamDescription.getFirst(),
                                             inputStreamDescription.getSecond());
    }

    private void consumeFileHandler(VirtualFile file, File data) {
        findOrCreateBlob(file).updateContent(null, data);
    }
}
