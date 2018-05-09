/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage;

import sirius.db.mixing.Column;
import sirius.db.mixing.Entity;
import sirius.db.mixing.EntityRef;
import sirius.db.mixing.annotations.AfterDelete;
import sirius.db.mixing.annotations.BeforeSave;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.kernel.di.std.Part;

import java.time.LocalDate;

/**
 * Represents a resized image version of a {@link VirtualObject}.
 */
public class VirtualObjectVersion extends Entity {

    /**
     * Contains the object to which this version belongs.
     */
    public static final Column VIRTUAL_OBJECT = Column.named("virtualObject");
    private final EntityRef<VirtualObject> virtualObject =
            EntityRef.on(VirtualObject.class, EntityRef.OnDelete.CASCADE);

    /**
     * Contains a copy of the bucket in which the virtual object resides, for faster access.
     */
    public static final Column BUCKET = Column.named("bucket");
    @Length(32)
    private String bucket;

    /**
     * Contains the version name which is WIDTHxHEIGTH.
     */
    public static final Column VERSION_KEY = Column.named("versionKey");
    @Length(32)
    private String versionKey;

    /**
     * Contains the physical storage key of this version.
     */
    public static final Column PHYSICAL_KEY = Column.named("physicalKey");
    @Length(64)
    @NullAllowed
    private String physicalKey;

    /**
     * Contains the file size in bytes.
     */
    public static final Column FILE_SIZE = Column.named("fileSize");
    private long fileSize = 0;

    /**
     * Contains the MD5 checksum of the data of this version.
     */
    public static final Column MD5 = Column.named("md5");
    @NullAllowed
    @Length(64)
    private String md5;

    /**
     * Contains the creation date of this version.
     */
    public static final Column CREATED_DATE = Column.named("createdDate");
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
            versionManager.clearCacheForVirtualObject(this.getVirtualObject().getValue());
            storage.clearCacheForVirtualObject(this.getVirtualObject().getValue());
        }
    }

    public EntityRef<VirtualObject> getVirtualObject() {
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
