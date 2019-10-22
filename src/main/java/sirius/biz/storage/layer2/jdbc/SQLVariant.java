/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2.jdbc;

import sirius.biz.storage.layer1.FileHandle;
import sirius.biz.storage.layer2.variants.BlobVariant;
import sirius.db.jdbc.SQLEntity;
import sirius.db.jdbc.SQLEntityRef;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.types.BaseEntityRef;

import java.time.LocalDateTime;
import java.util.Optional;

public class SQLVariant extends SQLEntity implements BlobVariant {

    public static final Mapping BLOB = Mapping.named("blob");
    private final SQLEntityRef<SQLBlob> blob = SQLEntityRef.on(SQLBlob.class, BaseEntityRef.OnDelete.CASCADE);

    public static final Mapping VARIANT_NAME = Mapping.named("variantName");
    @Length(64)
    private String variantName;

    public static final Mapping PHYSICAL_OBJECT_KEY = Mapping.named("physicalObjectKey");
    @Length(64)
    private String physicalObjectKey;

    public static final Mapping SIZE = Mapping.named("size");
    private long size = 0;

    public static final Mapping LAST_CONVERSION_ATTEMPT = Mapping.named("lastConversionAttempt");
    private LocalDateTime lastConversionAttempt = LocalDateTime.now();

    public static final Mapping NUM_ATTEMPTS = Mapping.named("numAttempts");
    private int numAttempts;

    public static final Mapping QUEUED_FOR_CONVERSION = Mapping.named("queuedForConversion");
    private boolean queuedForConversion;

    public static final Mapping NODE = Mapping.named("node");
    @Length(50)
    @NullAllowed
    private String node;

    @Override
    public Optional<FileHandle> download() {
        return Optional.empty();
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

    public SQLEntityRef<SQLBlob> getBlob() {
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
}
