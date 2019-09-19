/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2;

import io.netty.handler.codec.http.HttpResponseStatus;
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

import javax.annotation.Nullable;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Provides the adapter between the layer 2 (blob storage) and the layer 3 (browsable virtual file system).
 */
@Register
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
        protected Optional<VirtualFile> createPlaceholder(@Nullable Directory parentDirectory,
                                                          VirtualFile parent,
                                                          String name) {
            if (parentDirectory != null && parentDirectory.getStorageSpace().isReadonly()) {
                return Optional.empty();
            }

            MutableVirtualFile file = new MutableVirtualFile(parent, name);
            file.attach(new Placeholder(parent, name));
            attachHandlers(file);

            return Optional.of(file);
        }

        private VirtualFile wrapBlob(VirtualFile parent, Blob blob) {
            MutableVirtualFile file = new MutableVirtualFile(parent, blob.getFilename());
            file.attach(Blob.class, blob);
            attachHandlers(file);

            return file;
        }

        @Override
        public Optional<VirtualFile> findChild(VirtualFile parent, String name) {
            Optional<Directory> parentDirectory = parent.tryAs(Directory.class);
            if (parentDirectory.isPresent()) {
                return findChildInDirectory(parent, parentDirectory.get(), name);
            }

            if (parent.is(Placeholder.class)) {
                // If the parent is a placeholder then we have already ensured that the storage space
                // isn't readonly so therefore we can skip this check and pass null als parent directory.
                return createPlaceholder(null, parent, name);
            }

            return Optional.empty();
        }

        private Optional<VirtualFile> findChildInDirectory(VirtualFile parent, Directory parentDir, String name) {
            // If there is a file extension, this is most probably a file / blob. Therefore we first
            // try to resolve it as such and then fallback to resolving as directory...
            if (Strings.isFilled(Files.getFileExtension(name))) {
                return priorizedLookup(() -> parentDir.findChildBlob(name).map(blob -> wrapBlob(parent, blob)),
                                       () -> parentDir.findChildDirectory(name)
                                                      .map(directory -> wrapDirectory(parent, directory)),
                                       () -> createPlaceholder(parentDir, parent, name));
            } else {
                //...if there is no file extension we reverse the lookup order
                return priorizedLookup(() -> parentDir.findChildDirectory(name)
                                                      .map(directory -> wrapDirectory(parent, directory)),
                                       () -> parentDir.findChildBlob(name).map(blob -> wrapBlob(parent, blob)),
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
        private final Optional<VirtualFile> priorizedLookup(Supplier<Optional<VirtualFile>>... lookups) {
            for (Supplier<Optional<VirtualFile>> lookup : lookups) {
                Optional<VirtualFile> result = lookup.get();
                if (result.isPresent()) {
                    return result;
                }
            }

            return Optional.empty();
        }

        @Override
        public void enumerate(VirtualFile parent, FileSearch search) {
            //TODO search + limit
            Optional<Directory> parentDirectory = parent.tryAs(Directory.class);
            if (parentDirectory.isPresent()) {
                parentDirectory.get()
                               .listChildDirectories(null,
                                                     null,
                                                     directory -> search.processResult(wrapDirectory(parent,
                                                                                                     directory)));
                parentDirectory.get()
                               .listChildBlobs(null, null, null, blob -> search.processResult(wrapBlob(parent, blob)));
            }
        }
    }

    private VirtualFile wrapDirectory(VirtualFile parent, Directory directory) {
        MutableVirtualFile file = new MutableVirtualFile(parent, directory.getName());
        file.attach(Directory.class, directory);
        attachHandlers(file);

        return file;
    }

    /**
     * Maps the requested child names (which will be top-level directories) to
     * {@link BlobStorage#CONFIG_KEY_LAYER2_BROWSABLE browsable} storage spaces.
     *
     * @param parent the directory to resolve the child in
     * @param name   the name of the child to resolve
     * @return a virtaul file representing the storage space with the given name or an empty optional if none was found
     */
    @Override
    public Optional<VirtualFile> findChild(VirtualFile parent, String name) {
        if (!storage.isKnown(name)) {
            return Optional.empty();
        }

        BlobStorageSpace space = storage.getSpace(name);
        if (!space.isBrowsable()) {
            return Optional.empty();
        }

        return Optional.of(wrapDirectory(parent, space.getRoot(tenants.getRequiredTenant().getIdAsString())));
    }

    /**
     * Lists all {@link BlobStorage#CONFIG_KEY_LAYER2_BROWSABLE browsable} storage spaces.
     *
     * @param parent the directory to enumerate
     * @param search the search criteria and result collector to use
     */
    @Override
    public void enumerate(VirtualFile parent, FileSearch search) {
        storage.getSpaces()
               .filter(BlobStorageSpace::isBrowsable)
               .map(space -> space.getRoot(tenants.getRequiredTenant().getIdAsString()))
               .map(directory -> wrapDirectory(parent, directory))
               .forEach(search::processResult);
    }

    @Override
    public int getPriority() {
        return DEFAULT_PRIORITY;
    }

    private void attachHandlers(MutableVirtualFile file) {
        file.withCanCreateChildren(this::canCreateChildren);
        file.withChildren(directoryChildProvider);
        file.withCanRenameHandler(this::isMutable);
        file.withRenameHandler(this::renameHandler);
        file.withCanDeleteHandler(this::isMutable);
        file.withDeleteHandler(this::deleteHandler);
        file.withCanMoveHandler(this::isMutable);
        file.withMoveHandler(this::moveHandler);
        file.withCanCreateDirectoryHandler(this::canCreateDirectoryHandler);
        file.withCreateDirectoryHandler(this::createDirectoryHandler);
        file.withDirectoryFlagSupplier(this::directoryFlagSupplier);
        file.withExistsFlagSupplier(this::existsFlagSupplier);
        file.withLastModifiedSupplier(this::lastModifiedSupplier);
        file.withSizeSupplier(this::sizeSupplier);
        file.withCanProvideInputStream(this::isReadable);
        file.withInputStreamSupplier(this::inputStreamSupplier);
        file.withCustomTunnelHandler(this::tunnelHandler);
        file.withCanProvideOutputStream(this::isWriteable);
        file.withOutputStreamSupplier(this::outputStreamSupplier);
        file.withCanConsumeStream(this::isWriteable);
        file.withConsumeStreamHandler(this::consumeStreamHandler);
        file.withCanConsumeFile(this::isWriteable);
        file.withConsumeFileHandler(this::consumeFileHandler);
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

    private boolean moveHandler(VirtualFile file, VirtualFile newParent) {
        Optional<Directory> directory = file.tryAs(Directory.class);
        if (directory.isPresent()) {
            directory.get().move(newParent.tryAs(Directory.class).orElse(null));
            return true;
        }

        Optional<Blob> blob = file.tryAs(Blob.class);
        if (blob.isPresent()) {
            blob.get().move(newParent.tryAs(Directory.class).orElse(null));
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
