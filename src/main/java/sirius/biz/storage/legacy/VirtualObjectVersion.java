/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.legacy;

import sirius.biz.storage.layer2.jdbc.SQLBlobStorage;
import sirius.db.jdbc.SQLEntity;
import sirius.db.jdbc.SQLEntityRef;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.AfterDelete;
import sirius.db.mixing.annotations.BeforeSave;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.kernel.di.std.Framework;
import sirius.kernel.di.std.Part;

import java.time.LocalDate;

/**
 * Represents a resized image version of a {@link VirtualObject}.
 * @deprecated use the new storage APIs
 */
@Deprecated
@Framework(SQLBlobStorage.FRAMEWORK_JDBC_BLOB_STORAGE)
public class VirtualObjectVersion extends SQLEntity {

    /**
     * Contains the object to which this version belongs.
     */
    public static final Mapping VIRTUAL_OBJECT = Mapping.named("virtualObject");
    private final SQLEntityRef<VirtualObject> virtualObject =
            SQLEntityRef.on(VirtualObject.class, SQLEntityRef.OnDelete.CASCADE);

    /**
     * Contains a copy of the bucket in which the virtual object resides, for faster access.
     */
    public static final Mapping BUCKET = Mapping.named("bucket");
    @Length(32)
    private String bucket;

    /**
     * Contains the version name which is WIDTHxHEIGTH.
     */
    public static final Mapping VERSION_KEY = Mapping.named("versionKey");
    @Length(32)
    private String versionKey;

    /**
     * Contains the physical storage key of this version.
     */
    public static final Mapping PHYSICAL_KEY = Mapping.named("physicalKey");
    @Length(64)
    @NullAllowed
    private String physicalKey;

    /**
     * Contains the file size in bytes.
     */
    public static final Mapping FILE_SIZE = Mapping.named("fileSize");
    private long fileSize;

    /**
     * Contains the MD5 checksum of the data of this version.
     */
    public static final Mapping MD5 = Mapping.named("md5");
    @NullAllowed
    @Length(64)
    private String md5;

    /**
     * Contains the creation date of this version.
     */
    public static final Mapping CREATED_DATE = Mapping.named("createdDate");
    private LocalDate createdDate;

    @Part
    private static Storage storage;

    @Part
    private static VersionManager versionManager;

    @BeforeSave
    protected void initDate() {
        if (createdDate == null) {
            createdDate = LocalDate.now();
        }
    }

    @AfterDelete
    protected void removePhysicalFile() {
        storage.deletePhysicalObject(getBucket(), getPhysicalKey());
    }

    @BeforeSave
    @AfterDelete
    protected void removeVersionFromCaches() {
        if (this.getVirtualObject().isFilled()) {
            versionManager.clearCacheForVirtualObject(this.getVirtualObject().fetchValue());
            storage.clearCacheForVirtualObject(this.getVirtualObject().fetchValue());
        }
    }

    public SQLEntityRef<VirtualObject> getVirtualObject() {
        return virtualObject;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getVersionKey() {
        return versionKey;
    }

    public void setVersionKey(String versionKey) {
        this.versionKey = versionKey;
    }

    public String getPhysicalKey() {
        return physicalKey;
    }

    public void setPhysicalKey(String physicalKey) {
        this.physicalKey = physicalKey;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getMd5() {
        return md5;
    }

    public void setMd5(String md5) {
        this.md5 = md5;
    }

    public LocalDate getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDate createdDate) {
        this.createdDate = createdDate;
    }
}
