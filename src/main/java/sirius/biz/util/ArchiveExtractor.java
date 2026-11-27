/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.util;

import net.sf.sevenzipjbinding.ArchiveFormat;
import net.sf.sevenzipjbinding.IInArchive;
import net.sf.sevenzipjbinding.SevenZip;
import net.sf.sevenzipjbinding.SevenZipException;
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream;
import sirius.biz.storage.layer3.VirtualFile;
import sirius.kernel.Sirius;
import sirius.kernel.async.TaskContext;
import sirius.kernel.commons.Amount;
import sirius.kernel.commons.Callback;
import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Files;
import sirius.kernel.commons.Processor;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.kernel.nls.NLS;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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
 * Utility to handle and extract archive files.
 * <p>
 * If the framework <tt>biz.seven-zip</tt> is enabled, this will load the native 7-ZIP bindings and support a
 * multitude of file formats like <tt>7z, tar, rar etc.</tt> otherwise "just" ZIP files are supported.
 * <p>
 * Note that using native code always bears a certain risk as an error there crashed the whole JVM. Therefore enabling
 * this is only advised on clusters which have dedicated worker servers.
 * <p>
 * Also note that ZIP files are always processed using the Java APIs as this is way simpler to process (can be
 * done in memory without allocating large buffers or resorting to disk - therefore this is also faster (as disk
 * access is always slower than executing Java-Code). Also 7-ZIP has some severe bugs when handling paths in ZIP
 * files which were produced by Microsoft Windows.
 * <p>
 * Note that {@link #getSupportedFileExtensions()} honors the framework selection and always returns an appropriate
 * list.
 */
@Register(classes = ArchiveExtractor.class)
public class ArchiveExtractor {

    private static final String FRAMEWORK_SEVEN_ZIP = "biz.seven-zip";
    private static final String ZIP_EXTENSION = "zip";

    /**
     * Names the charset used by DOS, and thus by most ZIP tools on Windows, in Western Europe.
     */
    private static final String DOS_CHARSET = "IBM850";

    private Set<String> supportedExtensions;
    private Boolean sevenZipEnabled = null;

    /**
     * Defines if and how existing files are overwritten.
     *
     * @see #updateFile(ExtractedFile, VirtualFile, OverrideMode)
     */
    public enum OverrideMode {
        /**
         * Overrides files if they changed (different size or newer "last modified")
         */
        ON_CHANGE,

        /**
         * Always overrides files.
         */
        ALWAYS,

        /**
         * Never overrides files.
         */
        NEVER;

        @Override
        public String toString() {
            return NLS.get(getClass().getSimpleName() + "." + name());
        }
    }

    /**
     * Defines the result of a file update operation.
     *
     * @see #updateFile(ExtractedFile, VirtualFile, OverrideMode)
     */
    public enum UpdateResult {
        /**
         * The file did not exist before and was created.
         */
        CREATED,

        /**
         * An existing file has been overridden.
         */
        UPDATED,

        /**
         * The existing file was not overridden due to the selected {@link OverrideMode}.
         */
        SKIPPED
    }

    /**
     * Builds a set of supported file extensions that can be extracted, all lowercased.
     *
     * @return all supported file extensions that can be processed as archive.
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

    private boolean isSevenZipEnabled() {
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
                Log.SYSTEM.INFO("Loading 7-ZIP native libraries to support all breeds of archive formats...");
                SevenZip.initSevenZipFromPlatformJAR();
                sevenZipEnabled = true;
            } catch (Throwable e) {
                Exceptions.handle()
                          .to(Log.SYSTEM)
                          .error(e)
                          .withSystemErrorMessage("Failed to initialize 7-Zip: %s (%s)")
                          .handle();
                sevenZipEnabled = false;
            }
        } else {
            sevenZipEnabled = false;
        }
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
    public boolean isZipFile(@Nullable String fileExtension) {
        return ZIP_EXTENSION.equalsIgnoreCase(fileExtension);
    }

    /**
     * Iterates over the items of an archive file.
     * <p>
     * Note that we use the filename as extra parameter instead of taking it from the given file, as this might
     * be completely random as it is most probably a temporary file created by
     * {@link sirius.biz.storage.layer1.FileHandle}.
     *
     * @param filename              the filename of the archive to extract
     * @param archiveFile           the archive file to extract
     * @param filter                determines which files will be processed
     * @param extractedFileConsumer invoked for each extracted file. May return <tt>false</tt> to abort processing or
     *                              <tt>true</tt> to continue
     * @see #extractAll(String, File, Predicate, Callback)
     */
    public void extract(String filename,
                        File archiveFile,
                        @Nullable Predicate<String> filter,
                        Processor<ExtractedFile, Boolean> extractedFileConsumer) {
        try {
            if (isZipFile(Files.getFileExtension(filename)) || !isSevenZipEnabled()) {
                extractZip(filename,
                           archiveFile,
                           enhanceFileFilter(filter),
                           extractedFileConsumer,
                           StandardCharsets.UTF_8,
                           true);
            } else {
                extract7z(filename, archiveFile, enhanceFileFilter(filter), extractedFileConsumer);
            }
        } catch (Exception exception) {
            throw Exceptions.handle()
                            .error(exception)
                            .withSystemErrorMessage("An error occurred while unzipping the archive '%s': %s (%s)",
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
        return Strings.isFilled(filename) && !Files.isConsideredHidden(filename) && !Files.isConsideredMetadata(path);
    }

    private void extractZip(String filename,
                            File archiveFile,
                            Predicate<String> filter,
                            Processor<ExtractedFile, Boolean> extractedFileConsumer,
                            Charset charset,
                            boolean retryWithLegacyCharset) throws Exception {
        try (ZipFile zipFile = new ZipFile(archiveFile, charset)) {
            extractZipEntriesFromZipFile(filter, extractedFileConsumer, zipFile);
        } catch (ZipException zipException) {
            if (retryWithLegacyCharset) {
                // Retry extraction using the charset of the tool which most probably created the archive
                Charset fallbackCharset = detectLegacyCharset(archiveFile);
                TaskContext.get()
                           .log("Cannot unzip the archive '"
                                + filename
                                + "': "
                                + zipException.getMessage()
                                + ".\nFalling back to charset: "
                                + fallbackCharset.displayName());
                Exceptions.ignore(zipException);
                extractZip(filename, archiveFile, filter, extractedFileConsumer, fallbackCharset, false);
                return;
            }

            if (!isSevenZipEnabled()) {
                // This is most probably an error indicating an inconsistent ZIP archive. We therefore directly throw
                // a handled exception to avoid jamming the syslog...
                throw Exceptions.createHandled()
                                .error(zipException)
                                .withSystemErrorMessage("Failed to unzip the given archive '%s': %s (%s)", filename)
                                .handle();
            }
            // Retry extraction using 7zip
            TaskContext.get()
                       .log("Cannot unzip the archive '"
                            + filename
                            + "': "
                            + zipException.getMessage()
                            + ".\nFalling back to 7zip...");
            Exceptions.ignore(zipException);
            extract7z(filename, archiveFile, filter, extractedFileConsumer);
        }
    }

    /**
     * Determines the charset of the file names in a ZIP archive which does not store them as UTF-8.
     * <p>
     * Such an archive does not tell its charset. In practice, it is either the DOS charset of the system which
     * created it, as used by the Windows Explorer and most tools on Windows, or its Windows charset. For Western
     * European systems, these are {@link #DOS_CHARSET CP850} and CP1252, where the latter is read as ISO-8859-1, as
     * both agree on all letters.
     * <p>
     * ISO-8859-1 remains the default, so that an archive which has been read correctly so far keeps its names. The
     * DOS charset is only used if the names give more evidence for it than against it:
     * <ul>
     * <li>A byte in the range of <tt>0x80</tt> to <tt>0x9F</tt> speaks for the DOS charset, which places most of its
     * accented letters there. In ISO-8859-1, these are control characters which do not appear in file names.</li>
     * <li>A byte which is a letter in ISO-8859-1 but a graphical symbol in the DOS charset speaks against it.</li>
     * </ul>
     * A byte which is a letter in both charsets tells nothing. Names which are stored as UTF-8 by an archive which
     * mixes both kinds of names are skipped, as they are decoded independently of the given charset.
     *
     * @param archiveFile the archive to inspect
     * @return the charset to decode the file names with
     * @throws IOException if the archive cannot be read
     */
    private Charset detectLegacyCharset(File archiveFile) throws IOException {
        if (!Charset.isSupported(DOS_CHARSET)) {
            return StandardCharsets.ISO_8859_1;
        }

        Charset dosCharset = Charset.forName(DOS_CHARSET);
        int evidenceForDos = 0;
        int evidenceAgainstDos = 0;

        try (ZipFile latinZipFile = new ZipFile(archiveFile, StandardCharsets.ISO_8859_1);
             ZipFile dosZipFile = new ZipFile(archiveFile, dosCharset)) {
            Enumeration<? extends ZipEntry> latinEntries = latinZipFile.entries();
            Enumeration<? extends ZipEntry> dosEntries = dosZipFile.entries();
            while (latinEntries.hasMoreElements() && dosEntries.hasMoreElements()) {
                String latinName = latinEntries.nextElement().getName();
                String dosName = dosEntries.nextElement().getName();

                // Equal names either only consist of ASCII characters, or are stored as UTF-8
                if (!latinName.equals(dosName) && latinName.length() == dosName.length()) {
                    for (int index = 0; index < latinName.length(); index++) {
                        // ISO-8859-1 maps each byte to the character with the same code
                        char rawByte = latinName.charAt(index);
                        if (rawByte >= 0x80 && rawByte <= 0x9F) {
                            evidenceForDos++;
                        } else if (rawByte > 0x9F && Character.isLetter(rawByte) && !Character.isLetter(dosName.charAt(
                                index))) {
                            evidenceAgainstDos++;
                        }
                    }
                }
            }
        } catch (ZipException zipException) {
            // The archive is unreadable for other reasons than its file names, which is handled by the caller
            Exceptions.ignore(zipException);
            return StandardCharsets.ISO_8859_1;
        }

        return evidenceForDos > evidenceAgainstDos ? dosCharset : StandardCharsets.ISO_8859_1;
    }

    private void extractZipEntriesFromZipFile(Predicate<String> filter,
                                              Processor<ExtractedFile, Boolean> extractedFileConsumer,
                                              ZipFile zipFile) throws Exception {
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        int numberOfFiles = 0;
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            numberOfFiles++;

            if (!entry.isDirectory() && filter.test(entry.getName())) {
                Amount progress = Amount.of(numberOfFiles).divideBy(Amount.of(zipFile.size()));
                boolean shouldContinue = extractedFileConsumer.apply(new ExtractedZipFile(entry,
                                                                                          () -> zipFile.getInputStream(
                                                                                                  entry),
                                                                                          progress));
                if (!shouldContinue) {
                    return;
                }
            }
        }
    }

    private void extract7z(String filename,
                           File archiveFile,
                           Predicate<String> filter,
                           Processor<ExtractedFile, Boolean> extractedFileConsumer) throws Exception {
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(archiveFile, "r")) {
            RandomAccessFileInStream inputStream = new RandomAccessFileInStream(randomAccessFile);
            try (IInArchive archive = SevenZip.openInArchive(null, inputStream)) {
                SevenZipAdapter adapter = new SevenZipAdapter(archive, filter, extractedFileConsumer);
                archive.extract(null, false, adapter);
                adapter.throwIfFailed();
            } catch (SevenZipException sevenZipException) {
                // This is most probably an error indicating an inconsistent archive. We therefore directly throw
                // a handled exception to avoid jamming the syslog...
                throw Exceptions.createHandled()
                                .error(sevenZipException)
                                .withSystemErrorMessage("7-ZIP failed (the archive '%s' is probably corrupted): %s (%s)",
                                                        filename)
                                .handle();
            }
        }
    }

    /**
     * Iterates over all items of an archive file.
     * <p>
     * Note that we use the filename as extra parameter instead of taking it from the given file, as this might
     * be completely random as it is most probably a temporary file created by
     * {@link sirius.biz.storage.layer1.FileHandle}.
     *
     * @param filename              the filename of the archive to extract
     * @param archiveFile           the archive file to extract
     * @param filter                determines which files will be processed
     * @param extractedFileConsumer invoked for each extracted file
     * @see #extract(String, File, Predicate, Processor)
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
     * Provides a helper when storing an extracted file in the {@link sirius.biz.storage.layer3.VirtualFileSystem}.
     * <p>
     * This permits to select an appropriate {@link OverrideMode} so that e.g. only changed files will be updated.
     *
     * @param source       the extracted file to store
     * @param target       the target to write the file to
     * @param overrideMode the overwrite mode used to determine what to do if the target already exists
     * @return the result of the operation based on the given files and override mode
     * @throws IOException in case of an IO error
     */
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
