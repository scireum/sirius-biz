/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import sirius.biz.jobs.batch.BatchJob;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.Processes;
import sirius.biz.storage.layer3.FileOrDirectoryParameter;
import sirius.biz.storage.layer3.VirtualFile;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDate;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Provides an export job which writes a file.
 * <p>
 * The file is either attached to the resulting {@link sirius.biz.process.Process} or stored in the given
 * VFS directory.
 */
public abstract class FileExportJob extends BatchJob {

    @Part
    private static Processes processes;

    protected final VirtualFile destination;
    private VirtualFile fileDestination;

    /**
     * Creates a new job which writes into the given destination.
     *
     * @param destinationParameter the parameter used to select the destination for the file being written
     * @param process              the context in which the process will be executed
     */
    protected FileExportJob(FileOrDirectoryParameter destinationParameter, ProcessContext process) {
        super(process);
        this.destination = process.getParameter(destinationParameter).orElse(null);
    }

    /**
     * Creates an output stream which writes to the destination selected by the user.
     *
     * @return the output stream to write the result to
     */
    protected OutputStream createOutputStream() {
        try {
            if (destination != null) {
                if (shouldUseProvidedOutputFile()) {
                    fileDestination = destination;
                } else if (shouldUseProvidedOutputDirectory()) {
                    fileDestination = createUniqueFile(destination);
                    process.updateTitle(process.getTitle() + ": " + fileDestination.toString());
                }
                return fileDestination.createOutputStream();
            }

            return process.addFile(determineFilenameWithoutExtension() + "." + determineFileExtension());
        } catch (IOException e) {
            throw process.handle(e);
        }
    }

    /**
     * Tries to create a new file in the given directory by generating a unique name.
     *
     * @param baseDirectory the base directory to create the file in
     * @return a new file with a unique name
     */
    protected VirtualFile createUniqueFile(VirtualFile baseDirectory) {
        VirtualFile result = baseDirectory.resolve(determineEffectiveFilename(""));
        int counter = 1;
        while (result.exists() && counter < 99) {
            result = baseDirectory.resolve(determineEffectiveFilename("-" + counter));
            counter++;
        }

        if (result.exists()) {
            throw Exceptions.createHandled()
                            .withNLSKey("FileExportJobs.tooManyFiles")
                            .set("firstFile", determineEffectiveFilename(""))
                            .set("lastFile", result.name())
                            .handle();
        }

        return result;
    }

    /**
     * Determines if the file selected in <tt>output</tt> should be used as destination.
     *
     * @return <tt>true</tt> if the file should be used, <tt>false</tt> otherwise
     */
    private boolean shouldUseProvidedOutputFile() {
        if (destination.exists()) {
            return destination.isFile();
        }

        return destination.parent().exists() && Strings.isFilled(destination.fileExtension());
    }

    /**
     * Determines if the directory selected in <tt>output</tt> should be used as destination.
     *
     * @return <tt>true</tt> if the directory should be used, <tt>false</tt> otherwise
     */
    private boolean shouldUseProvidedOutputDirectory() {
        return destination.exists() && destination.isDirectory();
    }

    /**
     * Determines the effective file name to use.
     *
     * @param suffix an optional suffix to append to the name to generate a unique file name
     * @return a full file name including the file extension
     */
    protected String determineEffectiveFilename(String suffix) {
        LocalDate today = LocalDate.now();
        return determineFilenameWithoutExtension()
               + "-"
               + today.getYear()
               + "-"
               + today.getMonthValue()
               + "-"
               + today.getDayOfMonth()
               + suffix
               + "."
               + determineFileExtension();
    }

    /**
     * Determines the file extension to use.
     *
     * @return the file extension to use
     */
    protected abstract String determineFileExtension();

    /**
     * Determines the base name to use for the file.
     * <p>
     * This will be expanded by the date and also by additional suffixes to generate a unique name. Also the file
     * extension as supplied by {@link #determineFileExtension()} will be appended.
     *
     * @return the base file name to use
     */
    protected abstract String determineFilenameWithoutExtension();

    /**
     * Digests the fresh created export file.
     * <p>
     * Override this method in order to perform validations on the final exported file.
     *
     * @param digester a consumer receiving the {@link InputStream} of the exported file
     */
    protected void digestExportedFile(Consumer<InputStream> digester) {
        if (fileDestination != null) {
            processInputStream(fileDestination::createInputStream, digester);
        } else {
            processInputStream(() -> {
                return processes.getFile(process.getProcessId(),
                                         determineFilenameWithoutExtension() + "." + determineFileExtension());
            }, digester);
        }
    }

    private void processInputStream(@Nonnull Supplier<InputStream> inputStreamSupplier,
                                    @Nonnull Consumer<InputStream> digester) {
        try (InputStream inputStream = inputStreamSupplier.get()) {
            digester.accept(inputStream);
        } catch (IOException e) {
            process.handle(e);
        }
    }
}
