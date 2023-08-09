/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.legacy;

import sirius.biz.storage.layer2.jdbc.SQLBlobStorage;
import sirius.biz.tenants.jdbc.SQLTenantAware;
import sirius.db.KeyGenerator;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.AfterDelete;
import sirius.db.mixing.annotations.BeforeSave;
import sirius.db.mixing.annotations.ComplexDelete;
import sirius.db.mixing.annotations.Index;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.kernel.commons.Files;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Framework;
import sirius.kernel.di.std.Part;
import sirius.web.http.MimeHelper;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Stores the metadata for an object managed by the {@link Storage}.
 * <p>
 * Note that the externally visible methods are decalred by {@link StoredObject} which is the public interface.
 *
 * @deprecated use the new storage APIs
 */
@Index(name = "object_key_lookup", columns = "objectKey", unique = true)
@Index(name = "path_lookup", columns = {"tenant", "bucket", "path"})
@Index(name = "reference_lookup", columns = "reference")
@Index(name = "temporary_lookup", columns = {"temporary", "trace_changedAt"})
@Index(name = "cleanup_lookup", columns = {"bucket", "trace_changedAt"})
@Framework(SQLBlobStorage.FRAMEWORK_JDBC_BLOB_STORAGE)
@ComplexDelete(false)
@Deprecated
public class VirtualObject extends SQLTenantAware implements StoredObject {

    /**
     * Contains the bucket in which the object resides.
     */
    public static final Mapping BUCKET = Mapping.named("bucket");
    @Length(32)
    private String bucket;

    /**
     * Contains the unique object key of this object.
     */
    public static final Mapping OBJECT_KEY = Mapping.named("objectKey");
    @Length(64)
    private String objectKey;

    /**
     * Contains the physical key, which points to the actual contents of this object.
     */
    public static final Mapping PHYSICAL_KEY = Mapping.named("physicalKey");
    @Length(64)
    @NullAllowed
    private String physicalKey;

    /**
     * Contains the size in bytes of this object.
     */
    public static final Mapping FILE_SIZE = Mapping.named("fileSize");
    private long fileSize;

    /**
     * Contains the MD5 checksum of the stored data.
     */
    public static final Mapping MD5 = Mapping.named("md5");
    @NullAllowed
    @Length(64)
    private String md5;

    /**
     * Contains the virtual path of the object.
     * <p>
     * The length is shorten than a UNIX path (255 chars) to permit proper indexing in the database (the max key length
     * in MySQL is typically 767 bytes).
     */
    public static final Mapping PATH = Mapping.named("path");
    @Length(200)
    @NullAllowed
    private String path;

    /**
     * Contains the file extension used to determine the mime type.
     */
    public static final Mapping FILE_EXTENSION = Mapping.named("fileExtension");
    @Length(10)
    @NullAllowed
    private String fileExtension;

    /**
     * Contains a reference to another entity, if this object was specifically created for a reference.
     * <p>
     * When an object is uploaded / created for a reference, the field name and object name is placed here. This makes
     * the object invisible in the UI and also automatically deletes it if the referencing object is deleted.
     */
    public static final Mapping REFERENCE = Mapping.named("reference");
    @Length(255)
    @NullAllowed
    private String reference;

    /**
     * Marks this object as temporary.
     * <p>
     * If a new file is uploaded for a reference it is marked as temporary. If the referencing entity is saved and
     * confirms the object key, this flag is removed. Otherwise, if the file is uploaded but the entity is
     * never saved, the file will be eventually deleted by {@link StorageCleanupTask}.
     */
    public static final Mapping TEMPORARY = Mapping.named("temporary");
    private boolean temporary;

    @Part
    private static Storage storage;

    @Part
    private static VersionManager versionManager;

    @Part
    private static KeyGenerator keyGen;

    @BeforeSave(priority = 110)
    protected void ensureUniquenessOfPath() {
        if (Strings.isEmpty(reference) && !temporary) {
            assertUnique(PATH, getPath(), TENANT, BUCKET);
        }
    }

    @BeforeSave
    protected void fillObjectKey() {
        if (Strings.isEmpty(objectKey)) {
            setObjectKey(keyGen.generateId());
        }
    }

    @BeforeSave
    protected void fixPath() {
        fileExtension = null;

        if (Strings.isEmpty(path)) {
            return;
        }
        path = Storage.normalizePath(path);

        if (path == null) {
            return;
        }

        fileExtension = Files.getFileExtension(path);
    }

    @Override
    public LocalDateTime getLastModified() {
        LocalDateTime result = getTrace().getChangedAt();
        return Objects.requireNonNullElseGet(result, LocalDateTime::now);
    }

    @AfterDelete
    protected void removePhysicalObject() {
        storage.deletePhysicalObject(getBucket(), getPhysicalKey());
    }

    @BeforeSave(priority = 110)
    @AfterDelete
    protected void removeObjectFromCaches() {
        if (!isNew()) {
            versionManager.clearCacheForVirtualObject(this);
            storage.clearCacheForVirtualObject(this);
        }
    }

    @Override
    public DownloadBuilder prepareURL() {
        return storage.prepareDownload(this);
    }

    @Override
    public String getFilename() {
        return Strings.splitAtLast(getPath(), "/").getSecond();
    }

    @Override
    public String toString() {
        return getFilename();
    }

    public boolean isImage() {
        return MimeHelper.guessMimeType(getPath()).startsWith("image");
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    @Override
    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public String getPhysicalKey() {
        return physicalKey;
    }

    public void setPhysicalKey(String physicalKey) {
        this.physicalKey = physicalKey;
    }

    @Override
    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    @Override
    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getFileExtension() {
        return fileExtension;
    }

    public void setFileExtension(String fileExtension) {
        this.fileExtension = fileExtension;
    }

    @Override
    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getMd5() {
        return md5;
    }

    public void setMd5(String md5) {
        this.md5 = md5;
    }

    public boolean isTemporary() {
        return temporary;
    }

    public void setTemporary(boolean temporary) {
        this.temporary = temporary;
    }
}
