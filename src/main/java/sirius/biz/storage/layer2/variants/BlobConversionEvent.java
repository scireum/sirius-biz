/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2.variants;

import sirius.biz.analytics.events.Event;
import sirius.biz.storage.layer1.FileHandle;
import sirius.biz.storage.util.StorageUtils;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.kernel.di.std.Framework;

/**
 * Records the successful/failed conversion of a {@link BlobVariant} by a {@link Converter}.
 */
@Framework(StorageUtils.FRAMEWORK_STORAGE)
public class BlobConversionEvent extends Event {

    /**
     * Contains the ID of the tenant owning the source and target files being converted.
     */
    public static final Mapping TENANT = Mapping.named("tenant");
    @NullAllowed
    private String tenant;

    /**
     * Contains the key of the blob being converted.
     */
    public static final Mapping BLOB_KEY = Mapping.named("blobKey");
    private String blobKey;

    /**
     * Contains the file name of the blob.
     */
    public static final Mapping BLOB_FILE_NAME = Mapping.named("blobFilename");
    private String blobFilename;

    /**
     * Contains the name of the type / variant being created during conversion.
     */
    public static final Mapping VARIANT_NAME = Mapping.named("variantName");
    private String variantName;

    /**
     * Contains whether the conversion resulted in the desired output file.
     */
    public static final Mapping SUCCESSFUL = Mapping.named("successful");
    private boolean successful;

    /**
     * Contains an optional descriptive reason when the conversion failed.
     */
    public static final Mapping FAIL_REASON = Mapping.named("failReason");
    @NullAllowed
    private String failReason;

    /**
     * Contains how long the conversion took in milliseconds.
     */
    public static final Mapping CONVERSION_DURATION = Mapping.named("conversionDuration");
    private long conversionDuration;

    /**
     * Contains how long the conversion waited in the queue.
     */
    public static final Mapping QUEUE_DURATION = Mapping.named("queueDuration");
    private long queueDuration;

    /**
     * Contains how long the up and downloads of the result took.
     */
    public static final Mapping TRANSFER_DURATION = Mapping.named("transferDuration");
    private long transferDuration;

    /**
     * Contains the size of the input file in bytes.
     */
    public static final Mapping INPUT_SIZE = Mapping.named("inputSize");
    private long inputSize;

    /**
     * Contains the size of the output file in bytes.
     */
    public static final Mapping OUTPUT_SIZE = Mapping.named("outputSize");
    private long outputSize;

    /**
     * Fills the event based on the given conversion process.
     *
     * @param conversionProcess the process to fetch infos from
     * @return the event itself for fluent method calls
     */
    public BlobConversionEvent withConversionProcess(ConversionProcess conversionProcess) {
        this.tenant = conversionProcess.getBlobToConvert().getTenantId();
        this.blobKey = conversionProcess.getBlobToConvert().getBlobKey();
        this.variantName = conversionProcess.getVariantName();
        this.blobFilename = conversionProcess.getBlobToConvert().getFilename();
        this.inputSize = conversionProcess.getBlobToConvert().getSize();
        this.queueDuration = conversionProcess.getQueueDuration();
        this.conversionDuration = conversionProcess.getConversionDuration();
        this.transferDuration = conversionProcess.getTransferDuration();

        return this;
    }

    /**
     * Specifies the output file which has been generated.
     *
     * @param handle the output file
     * @return the event itself for fluent method calls
     */
    public BlobConversionEvent withOutputFile(FileHandle handle) {
        if (handle.exists()) {
            this.successful = true;
            this.outputSize = handle.getFile().length();
        }

        return this;
    }

    /**
     * Adds and records a conversion error.
     *
     * @param exception the error to record
     * @return the event itself for fluent method calls
     */
    public BlobConversionEvent withConversionError(Throwable exception) {
        this.successful = false;
        this.failReason = exception.getMessage() + " (" + exception.getClass().getSimpleName() + ")";

        return this;
    }

    public String getTenant() {
        return tenant;
    }

    public String getBlobKey() {
        return blobKey;
    }

    public String getBlobFilename() {
        return blobFilename;
    }

    public String getVariantName() {
        return variantName;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public String getFailReason() {
        return failReason;
    }

    public long getConversionDuration() {
        return conversionDuration;
    }

    public long getQueueDuration() {
        return queueDuration;
    }

    public long getTransferDuration() {
        return transferDuration;
    }

    public long getInputSize() {
        return inputSize;
    }

    public long getOutputSize() {
        return outputSize;
    }
}
