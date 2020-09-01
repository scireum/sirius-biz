/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.util;

import com.google.common.io.ByteSource;
import net.sf.sevenzipjbinding.ArchiveFormat;
import net.sf.sevenzipjbinding.ExtractOperationResult;
import net.sf.sevenzipjbinding.IInArchive;
import net.sf.sevenzipjbinding.SevenZip;
import net.sf.sevenzipjbinding.SevenZipException;
import net.sf.sevenzipjbinding.SevenZipNativeInitializationException;
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream;
import sirius.kernel.nls.NLS;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Utility to handle and extract archive files like zip, 7z, tar, ...
 * <p>
 * For a list of supported formats have a look at {@link ArchiveFormat#values()}.
 */
public class ArchiveHelper {

    private ArchiveHelper() {
    }

    /**
     * Builds a list of supported file extensions that can be extracted, all lowercased.
     *
     * @return list of supported file extensions that can be extracted.
     */
    public static List<String> getSupportedFileExtensions() {
        return Collections.unmodifiableList(Arrays.stream(ArchiveFormat.values())
                                                  .map(archiveFormat -> archiveFormat.getMethodName().toLowerCase())
                                                  .collect(Collectors.toList()));
    }

    /**
     * Checks if the file extension is an archive which can be processed by 7z.
     *
     * @param fileExtension the extension to check
     * @return <tt>true</tt> when archive, <tt>false</tt> otherwise
     */
    public static boolean isArchiveFile(@Nullable String fileExtension) {
        return fileExtension != null && Arrays.stream(ArchiveFormat.values())
                                              .map(ArchiveFormat::getMethodName)
                                              .anyMatch(fileExtension::equalsIgnoreCase);
    }

    /**
     * Iterates over the items of an archive file
     *
     * @param archiveFile             the archive file to extract
     * @param filter                  will be called for each archive item. {@code unzipItemCallback} will be only called for this item if this filter unzipItemCallback returns true
     * @param progressAndStopProvider will be called for each archive item until it returns false
     * @throws IOException on extraction failure
     */
    public static void extract(File archiveFile,
                               Function<String, Boolean> filter,
                               Predicate<ExtractionProgress> progressAndStopProvider) throws IOException {
        try {
            initSevenZipLib();
        } catch (SevenZipNativeInitializationException e) {
            throw new IOException(NLS.fmtr("XMLImporter.sevenZipInitFailed").set("details", e.getMessage()).format());
        }

        try (RandomAccessFile randomAccessFile = new RandomAccessFile(archiveFile, "r")) {
            RandomAccessFileInStream inputStream = new RandomAccessFileInStream(randomAccessFile);
            try (IInArchive archive = SevenZip.openInArchive(null, inputStream)) {
                archive.extract(getCompleteArchiveIndices(archive),
                                false,
                                new LocalArchiveExtractCallback(archive, filter, progressAndStopProvider));
            }
        }
    }

    private static void initSevenZipLib() throws SevenZipNativeInitializationException {
        if (SevenZip.getUsedPlatform() == null) {
            SevenZip.initSevenZipFromPlatformJAR();
        }
    }

    @Nonnull
    private static int[] getCompleteArchiveIndices(@Nonnull IInArchive archive) throws SevenZipException {
        // could be done as a one-liner with Guava, but this one is about 10x faster
        int[] indices = new int[archive.getNumberOfItems()];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = i;
        }
        return indices;
    }

    /**
     * Provides a progress of a extraction process.
     */
    public static class ExtractionProgress {

        private final ExtractOperationResult extractOperationResult;
        private final ByteSource data;
        private final String filePath;
        private final long filesProcessed;
        private final long bytesProcessed;
        private final long totalBytes;

        /**
         * Creates a new instance for progress status updates.
         *
         * @param extractOperationResult the extraction result of the current file
         * @param data                   the data of the extracted file
         * @param filePath               the temp file path to the extracted file on disk
         * @param filesProcessed         the number of files processed
         * @param bytesProcessed         the number of bytes processed
         * @param totalBytes             the number of total bytes the archive has
         */
        public ExtractionProgress(ExtractOperationResult extractOperationResult,
                                  ByteSource data,
                                  String filePath,
                                  long filesProcessed,
                                  long bytesProcessed,
                                  long totalBytes) {
            this.extractOperationResult = extractOperationResult;
            this.data = data;
            this.filePath = filePath;
            this.filesProcessed = filesProcessed;
            this.bytesProcessed = bytesProcessed;
            this.totalBytes = totalBytes;
        }

        public ExtractOperationResult getExtractOperationResult() {
            return extractOperationResult;
        }

        public ByteSource getData() {
            return data;
        }

        public String getFilePath() {
            return filePath;
        }

        public long getFilesProcessed() {
            return filesProcessed;
        }

        public long getBytesProcessed() {
            return bytesProcessed;
        }

        public long getTotalBytes() {
            return totalBytes;
        }
    }
}
