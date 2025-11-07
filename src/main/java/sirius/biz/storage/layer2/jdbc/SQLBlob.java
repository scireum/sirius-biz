/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2.jdbc;

import sirius.biz.storage.layer1.FileHandle;
import sirius.biz.storage.layer2.Blob;
import sirius.biz.storage.layer2.BlobStorage;
import sirius.biz.storage.layer2.Directory;
import sirius.biz.storage.layer2.OptimisticCreate;
import sirius.biz.storage.layer2.URLBuilder;
import sirius.biz.storage.layer2.variants.BlobVariant;
import sirius.biz.storage.util.StorageUtils;
import sirius.db.KeyGenerator;
import sirius.db.jdbc.SQLEntity;
import sirius.db.jdbc.SQLEntityRef;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.BeforeSave;
import sirius.db.mixing.annotations.ComplexDelete;
import sirius.db.mixing.annotations.Index;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.LowerCase;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.annotations.Transient;
import sirius.db.mixing.annotations.TranslationSource;
import sirius.db.mixing.annotations.Unique;
import sirius.db.mixing.types.BaseEntityRef;
import sirius.kernel.async.Future;
import sirius.kernel.commons.Files;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Framework;
import sirius.kernel.di.std.Part;

import javax.annotation.Nullable;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Stores the metadata of a {@link Blob} in the underlying JDBC database.
 * <p>
 * Note that all non-trivial methods delegate to the associated {@link SQLBlobStorageSpace}.
 */
@Framework(SQLBlobStorage.FRAMEWORK_JDBC_BLOB_STORAGE)
@ComplexDelete(false)
@Index(name = "blob_key_lookup", columns = "blobKey", unique = true)
@Index(name = "blob_normalized_filename_lookup",
        columns = {"spaceName", "deleted", "parent", "normalizedFilename", "committed"})
@Index(name = "blob_sort_by_last_modified", columns = {"spaceName", "deleted", "parent", "lastModified"})
@Index(name = "blob_filename_lookup", columns = {"spaceName", "deleted", "filename", "parent", "committed"})
@Index(name = "blob_reference_lookup", columns = {"spaceName", "deleted", "reference", "referenceDesignator"})
@Index(name = "blob_created_loop", columns = "created")
@Index(name = "blob_renamed_loop", columns = "renamed")
@Index(name = "blob_content_updated_loop", columns = "contentUpdated")
@Index(name = "blob_deleted_loop", columns = "deleted")
@Index(name = "blob_parent_changed_loop", columns = "parentChanged")
@Index(name = "blob_delete_temporary_loop", columns = {"spaceName", "temporary", "deleted", "lastModified"})
@Index(name = "physical_object_lookup", columns = "physicalObjectKey")
@TranslationSource(Blob.class)
public class SQLBlob extends SQLEntity implements Blob, OptimisticCreate {

    @Part
    private static StorageUtils storageUtils;

    @Transient
    private SQLBlobStorageSpace space;

    /**
     * Contains the name of the space this blob resides in.
     */
    public static final Mapping SPACE_NAME = Mapping.named("spaceName");
    @Length(64)
    private String spaceName;

    /**
     * Contains the tenant id (if known) for which this blob was stored.
     */
    public static final Mapping TENANT_ID = Mapping.named("tenantId");
    @Length(64)
    @NullAllowed
    private String tenantId;

    /**
     * Contains the official blob key which is used to identify this blob.
     */
    public static final Mapping BLOB_KEY = Mapping.named("blobKey");
    @Unique
    @Length(64)
    private String blobKey;

    /**
     * Contains the actual Layer 1 storage object key which stores the current data of this blob.
     * <p>
     * Note that this will change once the blob is updated and that is also might be <tt>null</tt> if no data is
     * present.
     */
    public static final Mapping PHYSICAL_OBJECT_KEY = Mapping.named("physicalObjectKey");
    @NullAllowed
    @Length(64)
    private String physicalObjectKey;

    /**
     * Contains the filename if one was provided.
     */
    public static final Mapping FILENAME = Mapping.named("filename");
    @NullAllowed
    @Length(255)
    private String filename;

    /**
     * Contains the filename in lowercase (if one was provided).
     */
    public static final Mapping NORMALIZED_FILENAME = Mapping.named("normalizedFilename");
    @NullAllowed
    @LowerCase
    @Length(255)
    private String normalizedFilename;

    /**
     * Contains the file extension if a filename was provided.
     * <p>
     * For a <tt>test.pdf</tt> this would store "pdf" - which is the lowercase file extension without the ".".
     */
    public static final Mapping FILE_EXTENSION = Mapping.named("fileExtension");
    @NullAllowed
    @LowerCase
    @Length(255)
    private String fileExtension;

    /**
     * Contains an {@link sirius.db.mixing.Mixing#getUniqueName(Class, Object) unique object name} of an entity
     * for which this blob was stored.
     */
    public static final Mapping REFERENCE = Mapping.named("reference");
    @NullAllowed
    @Length(64)
    private String reference;

    /**
     * Contains the reference designator which is most probably the field name for which this blob was stored.
     */
    public static final Mapping REFERENCE_DESIGNATOR = Mapping.named("referenceDesignator");
    @NullAllowed
    @Length(50)
    private String referenceDesignator;

    /**
     * Contains the directory in which this blob resides.
     * <p>
     * Note that manually uploaded blobs (e.g. referenced by an entity field) have no parent.
     */
    public static final Mapping PARENT = Mapping.named("parent");
    @NullAllowed
    private final SQLEntityRef<SQLDirectory> parent =
            SQLEntityRef.on(SQLDirectory.class, BaseEntityRef.OnDelete.IGNORE).weak();

    /**
     * Stores if the blob was moved into another folder.
     */
    public static final Mapping PARENT_CHANGED = Mapping.named("parentChanged");
    private boolean parentChanged;

    /**
     * Stores if the blob was (is still) marked as temporary.
     */
    public static final Mapping TEMPORARY = Mapping.named("temporary");
    private boolean temporary;

    /**
     * Stores if the blob is marked as read-only.
     */
    public static final Mapping READ_ONLY = Mapping.named("readOnly");
    private boolean readOnly;

    /**
     * Contains the size (in bytes) of the blob data.
     */
    public static final Mapping SIZE = Mapping.named("size");
    private long size;

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
     * Stores the checksum of the file.
     */
    public static final Mapping CHECKSUM = Mapping.named("checksum");
    @NullAllowed
    private String checksum;

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
     * Stores if the blob was inserted.
     */
    public static final Mapping CREATED = Mapping.named("created");
    private boolean created;

    /**
     * Stores if the blob was renamed.
     */
    public static final Mapping RENAMED = Mapping.named("renamed");
    private boolean renamed;

    /**
     * Stores if the blob's content was updated.
     */
    public static final Mapping CONTENT_UPDATED = Mapping.named("contentUpdated");
    private boolean contentUpdated;

    @Part
    @Nullable
    private static BlobStorage layer2;

    @Part
    private static KeyGenerator keyGen;

    @BeforeSave
    protected void beforeSave() {
        if (Strings.isEmpty(blobKey)) {
            blobKey = keyGen.generateId();
        }

        updateFilenameFields();

        if (deleted) {
            // The blob has been deleted. Reset all other flags since it's now pointless to trigger any BlobChangedHandler.
            created = false;
            renamed = false;
            contentUpdated = false;
            parentChanged = false;
            return;
        }

        if (isNew() || isCreated()) {
            // New Blob entities have no physical object and won't be used by any loops until a blob is uploaded.
            // Blobs still marked as created have not yet been processed by the BlobCreatedHandler, therefore
            // it is pointless to set any other flag.
            return;
        }

        if (isChanged(FILENAME, NORMALIZED_FILENAME, FILE_EXTENSION)) {
            renamed = true;
        }
        if (isChanged(PHYSICAL_OBJECT_KEY)) {
            contentUpdated = true;
        }
        if (isChanged(PARENT)) {
            parentChanged = true;
        }
    }

    protected void updateFilenameFields() {
        if (Strings.isFilled(filename)) {
            this.filename = storageUtils.sanitizePath(filename);
            if (Strings.isFilled(filename)) {
                this.normalizedFilename = filename.toLowerCase();
                this.fileExtension = Files.getFileExtension(normalizedFilename);
            } else {
                this.filename = null;
                this.normalizedFilename = null;
                this.fileExtension = null;
            }
        }
    }

    @Override
    public SQLBlobStorageSpace getStorageSpace() {
        if (space == null) {
            space = (SQLBlobStorageSpace) layer2.getSpace(spaceName);
        }
        return space;
    }

    @Nullable
    @Override
    public Directory getParent() {
        return getStorageSpace().fetchBlobParent(this);
    }

    protected SQLEntityRef<SQLDirectory> getParentRef() {
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
    public Future tryCreateVariant(String variantName) {
        return getStorageSpace().tryCreateVariant(this, variantName);
    }

    @Override
    public Future tryCreateVariant(FileHandle inputFile, String variantName) {
        return getStorageSpace().tryCreateVariant(this, inputFile, variantName);
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
        getStorageSpace().move(this, (SQLDirectory) newParent);
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
        return Optional.ofNullable(getStorageSpace().findCompletedVariant(this, name));
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

    public SQLBlobStorageSpace getSpace() {
        return space;
    }

    public void setSpace(SQLBlobStorageSpace space) {
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

    public void setBlobKey(String blobKey) {
        this.blobKey = blobKey;
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

    @Nullable
    @Override
    public String getFileExtension() {
        return fileExtension;
    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public LocalDateTime getLastModified() {
        return lastModified;
    }

    @Nullable
    @Override
    public String getCheckSum() {
        return checksum;
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

    public boolean isCreated() {
        return created;
    }

    public boolean isRenamed() {
        return renamed;
    }

    public boolean isContentUpdated() {
        return contentUpdated;
    }

    public boolean isParentChanged() {
        return parentChanged;
    }

    @Override
    public LocalDateTime getLastTouched() {
        return lastTouched;
    }

    public void setLastTouched(LocalDateTime lastTouched) {
        this.lastTouched = lastTouched;
    }

    @Override
    public boolean isReadOnly() {
        return readOnly;
    }

    @Override
    public void setReadOnly(boolean readOnly) {
        getStorageSpace().updateBlobReadOnlyFlag(this, readOnly);
    }
}
