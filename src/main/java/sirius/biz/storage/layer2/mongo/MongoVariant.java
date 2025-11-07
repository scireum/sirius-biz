/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2.mongo;

import sirius.biz.storage.layer1.FileHandle;
import sirius.biz.storage.layer2.BasicBlobStorageSpace;
import sirius.biz.storage.layer2.variants.BlobVariant;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.AfterDelete;
import sirius.db.mixing.annotations.Index;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.annotations.SkipDefaultValue;
import sirius.db.mixing.types.BaseEntityRef;
import sirius.db.mongo.Mango;
import sirius.db.mongo.MongoEntity;
import sirius.db.mongo.types.MongoRef;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Framework;

import javax.annotation.Nullable;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Stores the metadata of a {@link BlobVariant} in the underlying MongoDB.
 * <p>
 * Note that all non-trivial methods delegate to the associated {@link MongoBlobStorage}.
 */
@Framework(MongoBlobStorage.FRAMEWORK_MONGO_BLOB_STORAGE)
@Index(name = "physical_key_lookup",
        columns = {"blob", "variantName"},
        columnSettings = {Mango.INDEX_ASCENDING, Mango.INDEX_ASCENDING})
@Index(name = "physical_object_lookup", columns = "physicalObjectKey", columnSettings = Mango.INDEX_ASCENDING)
public class MongoVariant extends MongoEntity implements BlobVariant {

    /**
     * References the raw blob from which this variant was derived.
     */
    public static final Mapping BLOB = Mapping.named("blob");
    private final MongoRef<MongoBlob> blob = MongoRef.writeOnceOn(MongoBlob.class, BaseEntityRef.OnDelete.CASCADE);

    /**
     * Contains the name / type of this variant.
     */
    public static final Mapping VARIANT_NAME = Mapping.named("variantName");
    private String variantName;

    /**
     * Contains the key of the layer1 object which holds the actual data.
     * <p>
     * Note that this remains empty until the conversion has been completed.
     */
    public static final Mapping PHYSICAL_OBJECT_KEY = Mapping.named("physicalObjectKey");
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
     * Stores the node name on which the last conversion was attempted.
     */
    public static final Mapping NODE = Mapping.named("node");
    private String node;

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
    @SkipDefaultValue
    private String checksum;

    @AfterDelete
    protected void onDelete() {
        MongoBlob mongoBlob = blob.forceFetchValue();
        if (mongoBlob == null) {
            return;
        }
        if (Strings.isFilled(physicalObjectKey)) {
            mongoBlob.getStorageSpace().getPhysicalSpace().delete(physicalObjectKey);
        }
        mongoBlob.getStorageSpace().purgeVariantFromCache(mongoBlob, variantName);
    }

    @Override
    public void delete() {
        mango.delete(this);
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

    public MongoRef<MongoBlob> getBlob() {
        return blob;
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

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }
}
