/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2.variants;

import sirius.biz.storage.layer1.FileHandle;
import sirius.biz.storage.layer2.Blob;
import sirius.kernel.commons.Producer;
import sirius.kernel.commons.Watch;

/**
 * Covers the task of performing a conversion of a given {@link Blob} into a {@link BlobVariant} of the desired type.
 * <p>
 * This class is sort of a data transfer type as it wraps both, the input parameters as well as the output/result and
 * some performance metrics.
 */
public class ConversionProcess {

    private final Blob blobToConvert;
    private final String variantName;
    private FileHandle fileHandle;
    private long queueDuration;
    private long transferDuration;
    private long conversionDuration;

    /**
     * Creates a new instance for the given blob and desired variant.
     *
     * @param blobToConvert the blob to convert
     * @param variantName   the target variant to create
     */
    public ConversionProcess(Blob blobToConvert, String variantName) {
        this.blobToConvert = blobToConvert;
        this.variantName = variantName;
    }

    public Blob getBlobToConvert() {
        return blobToConvert;
    }

    public String getVariantName() {
        return variantName;
    }

    /**
     * Records time the task spent in a queue waited to be executed.
     *
     * @param durationMillis the duration the task spent in the queue
     */
    protected void recordQueueDuration(long durationMillis) {
        this.queueDuration += durationMillis;
    }

    /**
     * Records the actual performance duration.
     * <p>
     * Each {@link Converter} should supply a value here so that proper metrics are created. Note
     * that this must not include any transfer times (i.e. downloading the source file).
     *
     * @param durationMillis the conversion duration in millis.
     */
    public void recordConversionDuration(long durationMillis) {
        this.conversionDuration += durationMillis;
    }

    /**
     * Convers the transfer process of the input data.
     * <p>
     * This should be used by the {@link Converter} when an input file is obtained so that
     * the metrics are filled properly.
     *
     * @param transfer the transfer to perform
     * @param <H>      the file type of the downloaded data (probably {@link FileHandle}).
     * @return the returned file itself
     * @throws Exception in case of an error during the download
     */
    public <H> H download(Producer<H> transfer) throws Exception {
        Watch transferWatch = Watch.start();
        try {
            return transfer.create();
        } finally {
            this.transferDuration += transferWatch.elapsedMillis();
        }
    }

    /**
     * Covers the upload process of the created variant file.
     * <p>
     * This is again used to compute proper metrics (the transfer duration in this case).
     *
     * @param task the upload task to monitor
     */
    public void upload(Runnable task) {
        Watch transferWatch = Watch.start();
        try {
            task.run();
        } finally {
            this.transferDuration += transferWatch.elapsedMillis();
        }
    }

    /**
     * Used to supply the actual conversion result.
     * <p>
     * Used by the {@link Converter} to supply the actual result of the conversion.
     *
     * @param fileHandle the file to use as variant
     */
    public void withConversionResult(FileHandle fileHandle) {
        this.fileHandle = fileHandle;
    }

    public FileHandle getResultFileHandle() {
        return fileHandle;
    }

    public long getQueueDuration() {
        return queueDuration;
    }

    public long getTransferDuration() {
        return transferDuration;
    }

    public long getConversionDuration() {
        return conversionDuration;
    }
}
