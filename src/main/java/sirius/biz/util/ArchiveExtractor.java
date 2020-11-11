/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.util;

import net.sf.sevenzipjbinding.ArchiveFormat;
import net.sf.sevenzipjbinding.IInArchive;
import net.sf.sevenzipjbinding.SevenZip;
import net.sf.sevenzipjbinding.SevenZipException;
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream;
import sirius.biz.storage.layer3.VirtualFile;
import sirius.kernel.Sirius;
import sirius.kernel.commons.Amount;
import sirius.kernel.commons.Callback;
import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Files;
import sirius.kernel.commons.Processor;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.kernel.nls.NLS;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * Utility to handle and extract archive files like zip, 7z, tar, ...
 * <p>
 * For a list of supported formats have a look at {@link ArchiveFormat#values()}.
 */
@Register(classes = ArchiveExtractor.class)
public class ArchiveExtractor {

    private static final String FRAMEWORK_SEVEN_ZIP = "biz.seven-zip";
    private static final String ZIP_EXTENSION = "zip";

    private Set<String> supportedExtensions;
    private Boolean sevenZipEnabled = null;

    public enum OverrideMode {
        ON_CHANGE, ALWAYS, NEVER;

        @Override
        public String toString() {
            return NLS.get(getClass().getSimpleName() + "." + name());
        }
    }

    public enum UpdateResult {
        CREATED, UPDATED, SKIPPED;

        @Override
        public String toString() {
            return NLS.get(getClass().getSimpleName() + "." + name());
        }
    }

    public boolean isSevenZipEnabled() {
        if (sevenZipEnabled == null) {
            checkAndInitSevenZip();
        }

        return sevenZipEnabled;
    }

    @SuppressWarnings("java:S1181")
    @Explain(
            "We really want to catch Throwable here as we're calling native code and we want to simply suppress using 7-ZIP if any trouble occurs.")
    private void checkAndInitSevenZip() {
        if (Sirius.isFrameworkEnabled(FRAMEWORK_SEVEN_ZIP)) {
            try {
                SevenZip.initSevenZipFromPlatformJAR();
                sevenZipEnabled = true;
            } catch (Throwable e) {
                Exceptions.handle()
                          .to(Log.SYSTEM)
                          .error(e)
                          .withSystemErrorMessage("Failed to inizialize 7-Zip: %s (%s)")
                          .handle();
                sevenZipEnabled = false;
            }
        } else {
            sevenZipEnabled = false;
        }
    }

    /**
     * Builds a list of supported file extensions that can be extracted, all lowercased.
     *
     * @return list of supported file extensions that can be extracted.
     */
    public Set<String> getSupportedFileExtensions() {
        if (supportedExtensions == null) {
            if (isSevenZipEnabled()) {
                supportedExtensions = Arrays.stream(ArchiveFormat.values())
                                            .map(archiveFormat -> archiveFormat.getMethodName().toLowerCase())
                                            .collect(Collectors.toSet());
            } else {
                supportedExtensions = Collections.singleton(ZIP_EXTENSION);
            }
        }

        return Collections.unmodifiableSet(supportedExtensions);
    }

    /**
     * Checks if the file extension is an archive which can be processed by this class.
     *
     * @param fileExtension the extension to check
     * @return <tt>true</tt> when this file is an archive, <tt>false</tt> otherwise
     */
    public boolean isArchiveFile(@Nullable String fileExtension) {
        return fileExtension != null && getSupportedFileExtensions().contains(fileExtension.toLowerCase());
    }

    /**
     * Determines if the given archive is a ZIP file.
     *
     * @param fileExtension the file extension of the file to check
     * @return <tt>true</tt> if the given file is a ZIP file, <tt>false</tt> otherwise
     */
    public boolean isZIPFile(@Nullable String fileExtension) {
        return ZIP_EXTENSION.equalsIgnoreCase(fileExtension);
    }

    /**
     * XXX TODO Iterates over the items of an archive file
     *
     * @param filename              the filename of the archive to extract
     * @param archiveFile           the archive file to extract
     * @param filter                determines which files will be processed
     * @param extractedFileConsumer invoked for each extracted file. May return <tt>false</tt> to abort processing or
     *                              <tt>true</tt> to continue
     */
    public void extractAll(String filename,
                           File archiveFile,
                           @Nullable Predicate<String> filter,
                           Callback<ExtractedFile> extractedFileConsumer) {
        extract(filename, archiveFile, filter, file -> {
            extractedFileConsumer.invoke(file);
            return true;
        });
    }

    /**
     * Iterates over the items of an archive file
     *
     * @param filename              the filename of the archive to extract
     * @param archiveFile           the archive file to extract
     * @param filter                determines which files will be processed
     * @param extractedFileConsumer invoked for each extracted file. May return <tt>false</tt> to abort processing or
     *                              <tt>true</tt> to continue
     */
    public void extract(String filename,
                        File archiveFile,
                        @Nullable Predicate<String> filter,
                        Processor<ExtractedFile, Boolean> extractedFileConsumer) {
        try {
            if (isZIPFile(Files.getFileExtension(filename))) {
                extractZIP(archiveFile, enhanceFileFilter(filter), extractedFileConsumer);
            } else {
                extract7z(archiveFile, enhanceFileFilter(filter), extractedFileConsumer);
            }
        } catch (Exception e) {
            throw Exceptions.handle()
                            .error(e)
                            .withSystemErrorMessage("An error occurred while unzipping an archive (%s): %s (%s)",
                                                    filename)
                            .handle();
        }
    }

    private Predicate<String> enhanceFileFilter(@Nullable Predicate<String> filter) {
        if (filter != null) {
            return filter.and(this::ignoreHiddenFiles);
        } else {
            return this::ignoreHiddenFiles;
        }
    }

    private boolean ignoreHiddenFiles(String path) {
        String filename = Files.getFilenameAndExtension(path);
        return !filename.startsWith(".") && !filename.startsWith("__MACOSX");
    }

    private void extractZIP(File archiveFile,
                            Predicate<String> filter,
                            Processor<ExtractedFile, Boolean> extractedFileConsumer) throws Exception {
        try (ZipFile zipFile = new ZipFile(archiveFile)) {
            extractZIPEntriesFromZipFile(filter, extractedFileConsumer, zipFile);
        } catch (ZipException zipException) {
            // This is most probably an error indicating an inconsistent ZIP archive. We therefore directly throw
            // a handled exception to avoid jamming the syslog...
            throw Exceptions.createHandled()
                            .error(zipException)
                            .withSystemErrorMessage("Failed to unzip the given archive: %s (%s)")
                            .handle();
        }
    }

    private void extractZIPEntriesFromZipFile(Predicate<String> filter,
                                              Processor<ExtractedFile, Boolean> extractedFileConsumer,
                                              ZipFile zipFile) throws Exception {
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        int numberOfFiles = 0;
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            numberOfFiles++;

            if (filter.test(entry.getName())) {
                Amount progress = Amount.of(numberOfFiles).divideBy(Amount.of(zipFile.size()));
                boolean shouldContinue = extractedFileConsumer.apply(new ExtractedZipFile(entry,
                                                                                          zipFile.getInputStream(entry),
                                                                                          progress));
                if (!shouldContinue) {
                    return;
                }
            }
        }
    }

    private void extract7z(File archiveFile,
                           Predicate<String> filter,
                           Processor<ExtractedFile, Boolean> extractedFileConsumer) throws Exception {
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(archiveFile, "r")) {
            RandomAccessFileInStream inputStream = new RandomAccessFileInStream(randomAccessFile);
            try (IInArchive archive = SevenZip.openInArchive(null, inputStream)) {
                archive.extract(null, false, new SevenZipAdapter(archive, filter, extractedFileConsumer));
            } catch (SevenZipException sevenZipException) {
                // This is most probably an error indicating an inconsistent archive. We therefore directly throw
                // a handled exception to avoid jamming the syslog...
                throw Exceptions.createHandled()
                                .error(sevenZipException)
                                .withSystemErrorMessage("7-ZIP failed (the archive is probably corrupted): %s (%s)")
                                .handle();
            }
        }
    }

    public UpdateResult updateFile(ExtractedFile source, VirtualFile target, OverrideMode overrideMode)
            throws IOException {
        if (!target.exists()) {
            try (InputStream in = source.openInputStream()) {
                target.consumeStream(in, source.size());
            }

            return UpdateResult.CREATED;
        }

        if (overrideMode == OverrideMode.NEVER) {
            return UpdateResult.SKIPPED;
        }

        if (overrideMode == OverrideMode.ALWAYS || isChanged(source, target)) {
            try (InputStream in = source.openInputStream()) {
                target.consumeStream(in, source.size());
            }

            return UpdateResult.UPDATED;
        } else {
            return UpdateResult.SKIPPED;
        }
    }

    private boolean isChanged(ExtractedFile source, VirtualFile target) {
        return source.size() != target.size() || target.lastModifiedDate().isBefore(source.lastModified());
    }
}
