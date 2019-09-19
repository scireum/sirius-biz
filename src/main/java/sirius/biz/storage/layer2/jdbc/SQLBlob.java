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
import sirius.biz.storage.layer2.BlobRevision;
import sirius.biz.storage.layer2.BlobStorage;
import sirius.biz.storage.layer2.BlobVariant;
import sirius.biz.storage.layer2.Directory;
import sirius.db.KeyGenerator;
import sirius.db.jdbc.SQLEntity;
import sirius.db.jdbc.SQLEntityRef;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.BeforeSave;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.annotations.Transient;
import sirius.db.mixing.annotations.Unique;
import sirius.db.mixing.types.BaseEntityRef;
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
 * Stores the metadata of a {@link Blob} in the underlying JDBC database.
 * <p>
 * Note that all non trivial methods delegate to the associated {@link SQLBlobStorageSpace}.
 */
@Framework(SQLBlobStorage.FRAMEWORK_JDBC_BLOB_STORAGE)
public class SQLBlob extends SQLEntity implements Blob {

    @Transient
    private SQLBlobStorageSpace space;

    public static final Mapping SPACE_NAME = Mapping.named("spaceName");
    @Length(64)
    private String spaceName;

    public static final Mapping TENANT_ID = Mapping.named("tenantId");
    @Length(64)
    @NullAllowed
    private String tenantId;

    public static final Mapping BLOB_KEY = Mapping.named("blobKey");
    @Unique
    @Length(64)
    private String blobKey;

    public static final Mapping PHYSICAL_OBJECT_ID = Mapping.named("physicalObjectId");
    @NullAllowed
    @Length(64)
    private String physicalObjectId;

    public static final Mapping FILENAME = Mapping.named("filename");
    @NullAllowed
    @Length(255)
    private String filename;

    public static final Mapping REFERENCE = Mapping.named("reference");
    @NullAllowed
    @Length(64)
    private String reference;

    public static final Mapping REFERENCE_DESIGNATOR = Mapping.named("referenceDesignator");
    @NullAllowed
    @Length(50)
    private String referenceDesignator;

    public static final Mapping PARENT = Mapping.named("parent");
    @NullAllowed
    private final SQLEntityRef<SQLDirectory> parent =
            SQLEntityRef.on(SQLDirectory.class, BaseEntityRef.OnDelete.REJECT);

    public static final Mapping HIDDEN = Mapping.named("hidden");
    private boolean hidden;

    public static final Mapping DELETED = Mapping.named("deleted");
    private boolean deleted;

    public static final Mapping TEMPORARY = Mapping.named("temporary");
    private boolean temporary;

    public static final Mapping SIZE = Mapping.named("size");
    private long size = 0;

    public static final Mapping LAST_MODIFIED = Mapping.named("lastModified");
    private LocalDateTime lastModified = LocalDateTime.now();

    @Part
    private static BlobStorage layer2;

    @Part
    private static KeyGenerator keyGen;

    @BeforeSave
    protected void beforeSave() {
        if (Strings.isEmpty(blobKey)) {
            blobKey = keyGen.generateId();
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
    public void deliver(Response response) {
        getStorageSpace().deliver(this, response);
    }

    @Override
    public void hide() {
        getStorageSpace().hide(this);
    }

    @Override
    public void delete() {
        getStorageSpace().delete(this);
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
        return getStorageSpace().createOutputStream(this, filename);
    }

    @Override
    public InputStream createInputStream() {
        return getStorageSpace().createInputStream(this);
    }

    @Override
    public boolean isTemporary() {
        return temporary;
    }

    @Override
    public List<BlobVariant> getVariants() {
        return getStorageSpace().fetchVariants(this);
    }

    @Override
    public List<BlobRevision> getRevisions() {
        return getStorageSpace().fetchRevisions(this);
    }

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

    public void setPhysicalObjectId(String physicalObjectId) {
        this.physicalObjectId = physicalObjectId;
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

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
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
    public String getPhysicalObjectId() {
        return physicalObjectId;
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
    public boolean isHidden() {
        return hidden;
    }
}
