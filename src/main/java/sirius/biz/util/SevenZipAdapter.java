/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.util;

import net.sf.sevenzipjbinding.ExtractAskMode;
import net.sf.sevenzipjbinding.ExtractOperationResult;
import net.sf.sevenzipjbinding.IArchiveExtractCallback;
import net.sf.sevenzipjbinding.IInArchive;
import net.sf.sevenzipjbinding.ISequentialOutStream;
import net.sf.sevenzipjbinding.PropID;
import net.sf.sevenzipjbinding.SevenZipException;
import sirius.kernel.async.TaskContext;
import sirius.kernel.commons.Amount;
import sirius.kernel.commons.Processor;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Acts as a callback between the 7-ZIP binding and our API as defined by {@link ExtractedFile}.
 * <p>
 * Note that due to the <i>interesting</i> nature of the 7-ZIP API, this contains a whole of stateful variables which
 * are set up in each of the callbacks and processed later (mainly in {@link #setOperationResult(ExtractOperationResult)}.
 */
class SevenZipAdapter implements IArchiveExtractCallback {

    private final IInArchive inArchive;
    private final Predicate<String> filter;
    private final TaskContext taskContext;
    private final Processor<ExtractedFile, Boolean> extractCallback;
    private final int totalFiles;
    private int filesExtracted;

    private boolean stop;
    private ExtractedFileBuffer currentBuffer;
    private String currentFilePath;
    private Instant currentLastModified;

    SevenZipAdapter(IInArchive inArchive, Predicate<String> filter, Processor<ExtractedFile, Boolean> extractCallback)
            throws SevenZipException {
        this.inArchive = inArchive;
        this.totalFiles = inArchive.getNumberOfItems();
        this.filter = filter;
        this.extractCallback = extractCallback;
        this.taskContext = TaskContext.get();
    }

    @Override
    public ISequentialOutStream getStream(int index, ExtractAskMode extractAskMode) throws SevenZipException {
        // Just to be sure, set all shared variables to a known state...
        if (currentBuffer != null) {
            currentBuffer.cleanup();
        }
        currentFilePath = null;

        currentLastModified = Optional.ofNullable((Date) inArchive.getProperty(index, PropID.LAST_MODIFICATION_TIME))
                                      .map(Date::getTime)
                                      .map(Instant::ofEpochMilli)
                                      .orElse(Instant.now());

        // An abort has been requested...
        if (stop || !taskContext.isActive()) {
            return null;
        }

        // No need to setup a buffer if no files is being extracted...
        if (extractAskMode != ExtractAskMode.EXTRACT) {
            return null;
        }

        filesExtracted++;

        // Skip over directories and hidden files...
        if (isDirectory(index) || isHidden(index)) {
            return null;
        }

        currentFilePath = fetchFilePath(index);

        // Enforce the filename filter...
        if (!filter.test(currentFilePath)) {
            return null;
        }

        // We actually want to extract this file - setup the shared buffer properly.
        // Note that this might (sooner or later) create a temporary file. Therefore it is essential that this stream
        // is closed.
        currentBuffer = new ExtractedFileBuffer();
        return data -> {
            try {
                currentBuffer.write(data);
                return data.length;
            } catch (IOException exception) {
                throw new SevenZipException(exception);
            }
        };
    }

    private boolean isHidden(int index) throws SevenZipException {
        Integer attributes = (Integer) inArchive.getProperty(index, PropID.ATTRIBUTES);
        return attributes != null && (attributes & PropID.AttributesBitMask.FILE_ATTRIBUTE_HIDDEN) != 0;
    }

    private boolean isDirectory(int index) throws SevenZipException {
        return Boolean.TRUE.equals(inArchive.getProperty(index, PropID.IS_FOLDER));
    }

    private String fetchFilePath(int index) throws SevenZipException {
        return (String) inArchive.getProperty(index, PropID.PATH);
    }

    @Override
    public void prepareOperation(ExtractAskMode extractAskMode) throws SevenZipException {
        // Ignored
    }

    @Override
    public void setOperationResult(ExtractOperationResult extractOperationResult) throws SevenZipException {
        if (!stop) {
            try {
                if (extractOperationResult != ExtractOperationResult.OK) {
                    // This is most likely an invalid archive. Therefore we use a handled exception here
                    // as there is no point in throwing this into the syslog...
                    throw Exceptions.createHandled()
                                    .withSystemErrorMessage("7-ZIP failed to extract file %s from archive: %s",
                                                            currentFilePath,
                                                            extractOperationResult.name())
                                    .handle();
                }

                // Notify our callback about the current result.
                // If this returns false, we abort any additional processing. We chose to use a flag here, which
                // is then checked in getStream() as well...
                Amount progress = Amount.of(filesExtracted).divideBy(Amount.of(totalFiles));
                LocalDateTime lastModified = LocalDateTime.ofInstant(currentLastModified, ZoneId.systemDefault());

                Extracted7ZFile extracted7ZFile = currentBuffer == null ?
                                                  null :
                                                  new Extracted7ZFile(currentBuffer,
                                                                      currentFilePath,
                                                                      lastModified,
                                                                      progress);
                stop = !extractCallback.apply(extracted7ZFile);
            } catch (Exception exception) {
                throw Exceptions.handle()
                                .to(Log.SYSTEM)
                                .error(exception)
                                .withSystemErrorMessage(
                                        "An error occurred while handling an extracted file: %s - %s (%s)",
                                        currentFilePath)
                                .handle();
            }
        }

        // We need to always close the buffer (if it is open) as it might drag a temporary file along...
        if (currentBuffer != null) {
            currentBuffer.cleanup();
        }
    }

    @Override
    public void setCompleted(long completeValue) throws SevenZipException {
        // Ignored
    }

    @Override
    public void setTotal(long total) throws SevenZipException {
        // Ignored
    }
}
