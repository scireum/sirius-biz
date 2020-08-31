/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.util;

import com.google.common.io.ByteSource;
import net.sf.sevenzipjbinding.ExtractOperationResult;

import javax.annotation.Nullable;

/**
 * Defines a callback interface used during archive extraction.
 */
@FunctionalInterface
public interface ArchiveExtractCallback {

    /**
     * Defines a method called for each item being extracted from an archive.
     *
     * @param status              the extract result
     * @param data                the raw data being exported
     * @param fileName            file name as defined in the archive
     * @param filesProcessedSoFar amount of files processed in the progress
     * @param bytesProcessedSoFar amount of bytes processed in the progress
     * @param totalBytes          total amount of bytes processed in the progress
     * @return <tt>true</tt> to continue with extraction or <tt>false</tt> to abort
     */
    boolean call(ExtractOperationResult status,
                 @Nullable ByteSource data,
                 String fileName,
                 long filesProcessedSoFar,
                 long bytesProcessedSoFar,
                 long totalBytes);
}