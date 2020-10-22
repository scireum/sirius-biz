/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2.mongo;

import sirius.biz.storage.layer1.FileHandle;
import sirius.biz.storage.layer2.Blob;
import sirius.biz.storage.layer2.BlobStorage;
import sirius.biz.storage.layer2.Directory;
import sirius.biz.storage.layer2.OptimisticCreate;
import sirius.biz.storage.layer2.URLBuilder;
import sirius.biz.storage.layer2.variants.BlobVariant;
import sirius.db.KeyGenerator;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.BeforeSave;
import sirius.db.mixing.annotations.ComplexDelete;
import sirius.db.mixing.annotations.Index;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.annotations.Transient;
import sirius.db.mixing.annotations.Unique;
import sirius.db.mixing.types.BaseEntityRef;
import sirius.db.mongo.Mango;
import sirius.db.mongo.MongoEntity;
import sirius.db.mongo.types.MongoRef;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Framework;
import sirius.kernel.di.std.Part;
import sirius.web.http.Response;

import javax.annotation.Nullable;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Stores the metadata of a {@link Blob} in the underlying MongoDB.
 * <p>
 * Note that all non trivial methods delegate to the associated {@link MongoBlobStorageSpace}.
 */
@Framework(MongoBlobStorage.FRAMEWORK_MONGO_BLOB_STORAGE)
@ComplexDelete(false)
@Index(name = "blob_normalized_filename_lookup",
        columns = {"spaceName", "deleted", "normalizedFilename", "parent", "committed"},
        columnSettings = {Mango.INDEX_ASCENDING,
                          Mango.INDEX_ASCENDING,
                          Mango.INDEX_ASCENDING,
                          Mango.INDEX_ASCENDING,
                          Mango.INDEX_ASCENDING})
@Index(name = "blob_filename_lookup",
        columns = {"spaceName", "deleted", "filename", "parent", "committed"},
        columnSettings = {Mango.INDEX_ASCENDING,
                          Mango.INDEX_ASCENDING,
                          Mango.INDEX_ASCENDING,
                          Mango.INDEX_ASCENDING,
                          Mango.INDEX_ASCENDING})
@Index(name = "blob_reference_lookup",
        columns = {"spaceName", "deleted", "reference", "referenceDesignator"},
        columnSettings = {Mango.INDEX_ASCENDING, Mango.INDEX_ASCENDING, Mango.INDEX_ASCENDING, Mango.INDEX_ASCENDING})
public class MongoBlob extends MongoEntity implements Blob, OptimisticCreate {

    @Transient
    private MongoBlobStorageSpace space;

    /**
     * Contains the name of the space this blob resides in.
     */
    public static final Mapping SPACE_NAME = Mapping.named("spaceName");
    private String spaceName;

    /**
     * Contains the tenant id (if known) for which this blob was stored.
     */
    public static final Mapping TENANT_ID = Mapping.named("tenantId");
    @NullAllowed
    private String tenantId;

    /**
     * Contains the official blob key which is used to identify this blob.
     */
    public static final Mapping BLOB_KEY = Mapping.named("blobKey");
    @Unique
    private String blobKey;

    /**
     * Contains the actual Layer 1 storage object key which stores the current data of this blob.
     * <p>
     * Note that this will change once the blob is updated and that is also might be <tt>null</tt> if no data is
     * present.
     */
    public static final Mapping PHYSICAL_OBJECT_KEY = Mapping.named("physicalObjectKey");
    @NullAllowed
    private String physicalObjectKey;

    /**
     * Contains the filename if one was provided.
     */
    public static final Mapping FILENAME = Mapping.named("filename");
    @NullAllowed
    private String filename;

    /**
     * Contains the filename in lowercase (if one was provided).
     */
    public static final Mapping NORMALIZED_FILENAME = Mapping.named("normalizedFilename");
    @NullAllowed
    private String normalizedFilename;

    /**
     * Contains the file extension if a filename was provided.
     * <p>
     * For a <tt>test.pdf</tt> this would store "pdf" - which is the lowercased file extension without the ".".
     */
    public static final Mapping FILE_EXTENSION = Mapping.named("fileExtension");
    @NullAllowed
    private String fileExtension;

    /**
     * Contains an {@link sirius.db.mixing.Mixing#getUniqueName(Class, Object) unique object name} of an entity
     * for which this blob was stored.
     */
    public static final Mapping REFERENCE = Mapping.named("reference");
    @NullAllowed
    private String reference;

    /**
     * Contains the reference designator which is most probably the field name for which this blob was stored.
     */
    public static final Mapping REFERENCE_DESIGNATOR = Mapping.named("referenceDesignator");
    @NullAllowed
    private String referenceDesignator;

    /**
     * Contains the directory in which this blob resides.
     * <p>
     * Note that manually uploaded blobs (e.g. referenced by an entity field) have no parent.
     */
    public static final Mapping PARENT = Mapping.named("parent");
    @NullAllowed
    private final MongoRef<MongoDirectory> parent = MongoRef.on(MongoDirectory.class, BaseEntityRef.OnDelete.IGNORE);

    /**
     * Stores if the blob was (is still) marked as temporary.
     */
    public static final Mapping TEMPORARY = Mapping.named("temporary");
    private boolean temporary;

    /**
     * Contains the size (in bytes) of the blob data.
     */
    public static final Mapping SIZE = Mapping.named("size");
    private long size = 0;

    /**
     * Stores the last modification timestamp.
     */
    public static final Mapping LAST_MODIFIED = Mapping.named("lastModified");
    private LocalDateTime lastModified = LocalDateTime.now();

    /**
     * Stores the last access timestamp.
     */
    public static final Mapping LAST_TOUCHED = Mapping.named("lastTouched");
    @NullAllowed
    private LocalDateTime lastTouched;

    /**
     * Stores if a blob has been fully initialized.
     * <p>
     * This is used by the optimistic locking algorithms to ensure that blob names remain unique
     * without requiring any locks.
     */
    public static final Mapping COMMITTED = Mapping.named("committed");
    private boolean committed;

    /**
     * Stores if the blob was marked as deleted.
     */
    public static final Mapping DELETED = Mapping.named("deleted");
    private boolean deleted;

    /**
     * Stores if the blob was marked as hidden.
     */
    public static final Mapping HIDDEN = Mapping.named("hidden");
    private boolean hidden;

    @Part
    private static BlobStorage layer2;

    @Part
    private static KeyGenerator keyGen;

    @BeforeSave
    protected void beforeSave() {
        if (Strings.isEmpty(blobKey)) {
            blobKey = keyGen.generateId();
        }

        updateFilenameFields();
    }

    protected void updateFilenameFields() {
        if (Strings.isFilled(filename)) {
            this.filename = filename.trim();
            if (Strings.isFilled(filename)) {
                this.normalizedFilename = filename.toLowerCase();
                this.fileExtension = Strings.splitAtLast(normalizedFilename, ".").getSecond();
            } else {
                this.filename = null;
                this.normalizedFilename = null;
                this.fileExtension = null;
            }
        }
    }

    @Override
    public MongoBlobStorageSpace getStorageSpace() {
        if (space == null) {
            space = (MongoBlobStorageSpace) layer2.getSpace(spaceName);
        }
        return space;
    }

    @Nullable
    @Override
    public Directory getParent() {
        return getStorageSpace().fetchBlobParent(this);
    }

    protected MongoRef<MongoDirectory> getParentRef() {
        return parent;
    }

    @Nullable
    @Override
    public String getPath() {
        return getStorageSpace().determineBlobPath(this);
    }

    @Override
    public Optional<FileHandle> download() {
        return getStorageSpace().download(this);
    }

    @Override
    public void deliver(Response response) {
        getStorageSpace().deliver(getBlobKey(), URLBuilder.VARIANT_RAW, response);
    }

    @Override
    public void delete() {
        getStorageSpace().delete(this);
    }

    @Override
    public void touch() {
        getStorageSpace().touch(getBlobKey());
    }

    @Override
    public void move(Directory newParent) {
        getStorageSpace().move(this, (MongoDirectory) newParent);
    }

    @Override
    public void rename(String newName) {
        getStorageSpace().rename(this, newName);
    }

    @Override
    public void updateContent(@Nullable String filename, File file) {
        getStorageSpace().updateContent(this, filename, file);
    }

    @Override
    public void updateContent(@Nullable String filename, InputStream data, long contentLength) {
        getStorageSpace().updateContent(this, filename, data, contentLength);
    }

    @Override
    public OutputStream createOutputStream(@Nullable String filename) {
        return getStorageSpace().createOutputStream(this, filename, null);
    }

    @Override
    public OutputStream createOutputStream(Runnable completedCallback, @Nullable String filename) {
        return getStorageSpace().createOutputStream(this, filename, completedCallback);
    }

    @Override
    public InputStream createInputStream() {
        return getStorageSpace().createInputStream(this);
    }

    @Override
    public URLBuilder url() {
        return new URLBuilder(getStorageSpace(), this);
    }

    @Override
    public List<? extends BlobVariant> fetchVariants() {
        return getStorageSpace().fetchVariants(this);
    }

    @Override
    public Optional<BlobVariant> findVariant(String name) {
        return Optional.ofNullable(getStorageSpace().findVariant(this, name));
    }

    @Override
    public boolean isTemporary() {
        return temporary;
    }

    @Override
    public String getSpaceName() {
        return spaceName;
    }

    public void setSpaceName(String spaceName) {
        this.spaceName = spaceName;
    }

    public MongoBlobStorageSpace getSpace() {
        return space;
    }

    public void setSpace(MongoBlobStorageSpace space) {
        this.space = space;
    }

    public void setPhysicalObjectKey(String physicalObjectKey) {
        this.physicalObjectKey = physicalObjectKey;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getReferenceDesignator() {
        return referenceDesignator;
    }

    public void setReferenceDesignator(String referenceDesignator) {
        this.referenceDesignator = referenceDesignator;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public void setTemporary(boolean temporary) {
        this.temporary = temporary;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public void setLastModified(LocalDateTime lastModified) {
        this.lastModified = lastModified;
    }

    @Override
    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    @Override
    public String getBlobKey() {
        return blobKey;
    }

    @Override
    public String getPhysicalObjectKey() {
        return physicalObjectKey;
    }

    @Nullable
    @Override
    public String getFilename() {
        return filename;
    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public LocalDateTime getLastModified() {
        return lastModified;
    }

    @Override
    public boolean isCommitted() {
        return committed;
    }

    public void setCommitted(boolean committed) {
        this.committed = committed;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    @Override
    public LocalDateTime getLastTouched() {
        return lastTouched;
    }

    public void setLastTouched(LocalDateTime lastTouched) {
        this.lastTouched = lastTouched;
    }
}
