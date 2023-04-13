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

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Covers the task of performing a conversion of a given {@link Blob} into a {@link BlobVariant} of the desired type.
 * <p>
 * This class is sort of a data transfer type as it wraps both, the input parameters and the output/result and
 * some performance metrics.
 */
public class ConversionProcess {

    private final Blob blobToConvert;
    private File fileToConvert;
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
     * Converts the transfer process of the input data.
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
     * @return the object itself for fluent method calls
     */
    public ConversionProcess withConversionResult(FileHandle fileHandle) {
        this.fileHandle = fileHandle;
        return this;
    }

    /**
     * Provides the file to use as input for the conversion.
     * <p>
     * This is useful when the file already exists in the file system, skipping a new download from storage.
     *
     * @param fileToConvert the file to use as input
     * @return the object itself for fluent method calls
     * @see #obtainInputFile()
     */
    public ConversionProcess withInputFile(File fileToConvert) {
        this.fileToConvert = fileToConvert;
        return this;
    }

    /**
     * Returns a file handle to an input file for conversion.
     * <p>
     * This can either be a permanent file handle pointing to a previously supplied file, or a temporary one
     * pointing to a file freshly downloaded from the {@linkplain #blobToConvert blob}.
     *
     * @return a {@link FileHandle} to the file to use for conversion
     * @throws Exception if a file cannot be obtained
     */
    public FileHandle obtainInputFile() throws Exception {
        FileHandle inputFile = obtainFileToConvert();
        if (inputFile == null) {
            inputFile = download(() -> blobToConvert.download()
                                                    .orElseThrow(() -> new FileNotFoundException(
                                                            "Cannot obtain the file from blob key "
                                                            + blobToConvert.getBlobKey())));
        }
        return inputFile;
    }

    /**
     * Returns the file name of the input file used for conversion.
     * <p>
     * Files downloaded from external storage are temporary, with the .tmp extension. Conversions usually check if
     * the file extension is relevant for the target format, so in this case we must fall back to the blob's file name.
     * This will usually be the case for a recently downloaded file from a {@linkplain Blob} which will be chained
     * into several conversions.
     *
     * @return the file name of the supplied file if it's not a tmp file or the {@link Blob#getFilename()}
     */
    public String getEffectiveFileName() {
        if (fileToConvert != null && !fileToConvert.getName().endsWith(".tmp")) {
            return fileToConvert.getName();
        }
        return getBlobToConvert().getFilename();
    }

    private FileHandle obtainFileToConvert() throws FileNotFoundException {
        if (fileToConvert == null) {
            return null;
        }

        if (!fileToConvert.exists()) {
            throw new FileNotFoundException("File " + fileToConvert.getPath() + " does not exist");
        }

        return FileHandle.permanentFileHandle(fileToConvert);
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
