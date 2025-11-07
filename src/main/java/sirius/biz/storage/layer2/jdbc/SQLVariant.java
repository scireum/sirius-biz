/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2.jdbc;

import sirius.biz.storage.layer1.FileHandle;
import sirius.biz.storage.layer2.BasicBlobStorageSpace;
import sirius.biz.storage.layer2.variants.BlobVariant;
import sirius.db.jdbc.SQLEntity;
import sirius.db.jdbc.SQLEntityRef;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.AfterDelete;
import sirius.db.mixing.annotations.Index;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.types.BaseEntityRef;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Framework;

import javax.annotation.Nullable;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Stores the metadata of a {@link BlobVariant} in the underlying JDBC database.
 * <p>
 * Note that all non-trivial methods delegate to the associated {@link SQLBlobStorageSpace}.
 */
@Framework(SQLBlobStorage.FRAMEWORK_JDBC_BLOB_STORAGE)
@Index(name = "physical_key_lookup", columns = {"sourceBlob", "variantName"})
@Index(name = "physical_object_lookup", columns = "physicalObjectKey")
public class SQLVariant extends SQLEntity implements BlobVariant {

    /**
     * References the raw blob from which this variant was derived.
     */
    public static final Mapping SOURCE_BLOB = Mapping.named("sourceBlob");
    private final SQLEntityRef<SQLBlob> sourceBlob =
            SQLEntityRef.writeOnceOn(SQLBlob.class, BaseEntityRef.OnDelete.CASCADE);

    /**
     * Contains the name / type of this variant.
     */
    public static final Mapping VARIANT_NAME = Mapping.named("variantName");
    @Length(64)
    private String variantName;

    /**
     * Contains the key of the layer1 object which holds the actual data.
     * <p>
     * Note that this remains empty until the conversion has been completed.
     */
    public static final Mapping PHYSICAL_OBJECT_KEY = Mapping.named("physicalObjectKey");
    @Length(64)
    @NullAllowed
    private String physicalObjectKey;

    /**
     * Contains the file size of the converted file.
     */
    public static final Mapping SIZE = Mapping.named("size");
    private long size;

    /**
     * Contains the timestamp when the last conversion was attempted.
     */
    public static final Mapping LAST_CONVERSION_ATTEMPT = Mapping.named("lastConversionAttempt");
    private LocalDateTime lastConversionAttempt = LocalDateTime.now();

    /**
     * Counts the number of attempts to create this variant.
     */
    public static final Mapping NUM_ATTEMPTS = Mapping.named("numAttempts");
    private int numAttempts;

    /**
     * Determines if this is currently queued for conversion.
     */
    public static final Mapping QUEUED_FOR_CONVERSION = Mapping.named("queuedForConversion");
    private boolean queuedForConversion;

    /**
     * Stores how long the conversion took (in millis).
     */
    public static final Mapping CONVERSION_DURATION = Mapping.named("conversionDuration");
    private long conversionDuration;

    /**
     * Stores how long the conversion waited in the queue (in millis).
     */
    public static final Mapping QUEUE_DURATION = Mapping.named("queueDuration");
    private long queueDuration;

    /**
     * Stores how long the download and upload from and to the storage took (in millis).
     */
    public static final Mapping TRANSFER_DURATION = Mapping.named("transferDuration");
    private long transferDuration;

    /**
     * Stores the checksum of the variant.
     */
    public static final Mapping CHECKSUM = Mapping.named("checksum");
    @NullAllowed
    private String checksum;

    /**
     * Stores the node name on which the last conversion was attempted.
     */
    public static final Mapping NODE = Mapping.named("node");
    @Length(50)
    private String node;

    @AfterDelete
    protected void onDelete() {
        SQLBlob sqlBlob = sourceBlob.forceFetchValue();
        if (sqlBlob == null) {
            return;
        }
        if (Strings.isFilled(physicalObjectKey)) {
            sqlBlob.getStorageSpace().getPhysicalSpace().delete(physicalObjectKey);
        }
        sqlBlob.getStorageSpace().purgeVariantFromCache(sqlBlob, variantName);
    }

    @Override
    public void delete() {
        oma.delete(this);
    }

    @Override
    public Optional<FileHandle> download() {
        return Optional.empty();
    }

    @Override
    public boolean isFailed() {
        return getNumAttempts() >= BasicBlobStorageSpace.VARIANT_MAX_CONVERSION_ATTEMPTS;
    }

    @Override
    public String getVariantName() {
        return variantName;
    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public LocalDateTime getLastConversionAttempt() {
        return lastConversionAttempt;
    }

    public void setLastConversionAttempt(LocalDateTime lastConversionAttempt) {
        this.lastConversionAttempt = lastConversionAttempt;
    }

    @Override
    public int getNumAttempts() {
        return numAttempts;
    }

    public void setNumAttempts(int numAttempts) {
        this.numAttempts = numAttempts;
    }

    @Override
    public boolean isQueuedForConversion() {
        return queuedForConversion;
    }

    public void setQueuedForConversion(boolean queuedForConversion) {
        this.queuedForConversion = queuedForConversion;
    }

    public String getNode() {
        return node;
    }

    public void setNode(String node) {
        this.node = node;
    }

    public SQLEntityRef<SQLBlob> getSourceBlob() {
        return sourceBlob;
    }

    public void setVariantName(String variantName) {
        this.variantName = variantName;
    }

    public void setSize(long size) {
        this.size = size;
    }

    @Override
    public String getPhysicalObjectKey() {
        return physicalObjectKey;
    }

    public void setPhysicalObjectKey(String physicalObjectKey) {
        this.physicalObjectKey = physicalObjectKey;
    }

    @Override
    public long getConversionDuration() {
        return conversionDuration;
    }

    public void setConversionDuration(long conversionDuration) {
        this.conversionDuration = conversionDuration;
    }

    @Override
    public long getQueueDuration() {
        return queueDuration;
    }

    public void setQueueDuration(long queueDuration) {
        this.queueDuration = queueDuration;
    }

    @Override
    public long getTransferDuration() {
        return transferDuration;
    }

    public void setTransferDuration(long transferDuration) {
        this.transferDuration = transferDuration;
    }

    @Nullable
    @Override
    public String getCheckSum() {
        return checksum;
    }
}
