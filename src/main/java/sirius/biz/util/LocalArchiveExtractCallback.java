/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.util;

import com.google.common.io.ByteSource;
import net.sf.sevenzipjbinding.ExtractAskMode;
import net.sf.sevenzipjbinding.ExtractOperationResult;
import net.sf.sevenzipjbinding.IArchiveExtractCallback;
import net.sf.sevenzipjbinding.IInArchive;
import net.sf.sevenzipjbinding.ISequentialOutStream;
import net.sf.sevenzipjbinding.PropID;
import net.sf.sevenzipjbinding.SevenZipException;
import sirius.kernel.async.TaskContext;
import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Files;
import sirius.kernel.health.Exceptions;

import javax.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Defines an implementation of the {@link IArchiveExtractCallback} to be used during archive extraction.
 */
public class LocalArchiveExtractCallback implements IArchiveExtractCallback {

    private final IInArchive inArchive;
    private final Function<String, Boolean> filter;
    private ByteArrayOutputStream buffer;
    private boolean skipExtraction;
    private boolean stop;
    private String filePath;
    private long filesProcessed;
    private long bytesProcessed;
    private long totalBytes;
    private final Predicate<ArchiveHelper.ExtractionProgress> progressAndStopProvider;

    LocalArchiveExtractCallback(IInArchive inArchive,
                                Function<String, Boolean> filter,
                                Predicate<ArchiveHelper.ExtractionProgress> progressAndStopProvider) {
        this.inArchive = inArchive;
        this.filter = filter;
        this.progressAndStopProvider = progressAndStopProvider;
    }

    @Override
    public ISequentialOutStream getStream(int index, ExtractAskMode extractAskMode) throws SevenZipException {
        if (stop || extractAskMode != ExtractAskMode.EXTRACT || !TaskContext.get().isActive()) {
            return null;
        }

        Boolean isFolder = (Boolean) inArchive.getProperty(index, PropID.IS_FOLDER);
        filePath = (String) inArchive.getProperty(index, PropID.PATH);
        String fileName = Files.getFilenameAndExtension(filePath);
        Integer attributes = (Integer) inArchive.getProperty(index, PropID.ATTRIBUTES);

        skipExtraction = false;
        if (isFolder != null) {
            skipExtraction = isFolder;
        }
        if (attributes != null) {
            skipExtraction |= (attributes & PropID.AttributesBitMask.FILE_ATTRIBUTE_HIDDEN) != 0;
        }
        if (filePath != null) {
            skipExtraction |= filePath.startsWith("__MACOSX");
            if (filter != null) {
                skipExtraction |= !filter.apply(filePath);
            }
        }
        if (fileName != null) {
            // need to filter hidden files (starting with dot), because some tar implementations create
            // hidden index files (ending with xml, too)
            skipExtraction |= fileName.startsWith(".");
        }

        if (skipExtraction) {
            return null;
        }

        filePath = fixEncodingProblems(filePath, (String) inArchive.getProperty(index, PropID.HOST_OS));

        buffer = new ByteArrayOutputStream();
        return data -> {
            try {
                buffer.write(data);
                return data.length;
            } catch (IOException e) {
                throw new SevenZipException(e);
            }
        };
    }

    @Nonnull
    private String fixEncodingProblems(String filePath, String hostOS) {
        String newFilePath = fixWindowsEncoding(filePath, hostOS);
        newFilePath = fixOtherStrangeBug(newFilePath);
        return newFilePath;
    }

    @Nonnull
    private String fixWindowsEncoding(@Nonnull String filePath, String hostOS) {
        byte[] filePathBytes = filePath.getBytes(StandardCharsets.UTF_8);

        if (!"fat".equalsIgnoreCase(hostOS)) {
            // Windows writes FAT as "Host OS" in zip files.
            // We expect this behaviour only on zip files that were created on Windows.
            return filePath;
        }
        for (int i = 0; i < filePathBytes.length - 1; i++) {
            // Windows saves zip entry file paths in IBM437 Codepage,
            // the 7z lib interpretates them as Unicode code points and we retrieve them in UTF-8.
            //
            // This way
            // ... special characters in IBM437 (80..bf) become Control Characters (c2 80..9f)
            //     or other special characters (c2 a0..bf) in UTF-8
            // ... special characters in IBM437 (c0..ff) become other special characters (c3 80..bf) in UTF-8
            //
            // We cannot safely distinguish whether special characters are an encoding problem or are there in purpose,
            // but we can search for UTF-8 control characters (c2 80..9f) that usually aren't present in file names.
            boolean hasEncodingProblem = filePathBytes[i] == (byte) 0xc2
                                         && filePathBytes[i + 1] >= (byte) 0x80
                                         && filePathBytes[i + 1] <= (byte) 0x9f;
            // detect ß (IBM437 e1) that comes as á (UTF-8 c3 a1)
            hasEncodingProblem |= filePathBytes[i] == (byte) 0xc3 && filePathBytes[i + 1] == (byte) 0xa1;

            if (hasEncodingProblem) {
                try {
                    // the string has an encoding error with very very very high probability, repair it...
                    return new String(filePath.getBytes(StandardCharsets.ISO_8859_1), "IBM437");
                } catch (UnsupportedEncodingException e) {
                    Exceptions.ignore(e);
                    break;
                }
            }
        }
        return filePath;
    }

    @Nonnull
    @SuppressWarnings("squid:S1067")
    @Explain("Reducing operators won't increase code visibility")
    private String fixOtherStrangeBug(@Nonnull String filePath) {
        byte[] filePathBytes = filePath.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < filePathBytes.length - 3; i++) {
            // The bug interpretates UTF-8 as ISO-8859-1 (which doubles the number of characters)
            // and converts them back to UTF-8.
            // This way all possible 2-byte UTF-8 bytes (c0..df 80..bf) become 4 bytes: (c3 80..9f c2 80..bf)
            if (filePathBytes[i] == (byte) 0xc3
                && filePathBytes[i + 1] >= (byte) 0x80
                && filePathBytes[i + 1] <= (byte) 0x9f
                && filePathBytes[i + 2] == (byte) 0xc2
                && filePathBytes[i + 3] >= (byte) 0x80
                && filePathBytes[i + 3] <= (byte) 0xbf) {
                // the string has an encoding error with very high probability, repair it...
                return new String(filePath.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
            }
        }
        return filePath;
    }

    @Override
    public void prepareOperation(ExtractAskMode extractAskMode) throws SevenZipException {
        // do nothing
    }

    @Override
    public void setOperationResult(ExtractOperationResult extractOperationResult) throws SevenZipException {
        if (stop || skipExtraction) {
            return;
        }
        filesProcessed++;
        ByteSource byteSource = null;
        if (extractOperationResult == ExtractOperationResult.OK) {
            byteSource = new ByteSource() {
                @Override
                public InputStream openStream() {
                    return new ByteArrayInputStream(buffer.toByteArray());
                }
            };
        }

        // if callback returns false -> stop
        stop = !progressAndStopProvider.test(new ArchiveHelper.ExtractionProgress(extractOperationResult,
                                                                                  byteSource,
                                                                                  filePath,
                                                                                  filesProcessed,
                                                                                  bytesProcessed,
                                                                                  totalBytes));
    }

    @Override
    public void setCompleted(long completeValue) throws SevenZipException {
        bytesProcessed = completeValue;
    }

    @Override
    public void setTotal(long total) throws SevenZipException {
        totalBytes = total;
    }
}
