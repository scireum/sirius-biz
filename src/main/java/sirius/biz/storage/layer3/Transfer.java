/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3;

import sirius.biz.jobs.Jobs;
import sirius.biz.process.ProcessContext;
import sirius.biz.storage.layer2.Blob;
import sirius.biz.storage.util.Attempt;
import sirius.biz.storage.util.StorageUtils;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;

import javax.annotation.CheckReturnValue;
import java.io.InputStream;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Provides a configurable helper to either copy or move {@link VirtualFile virtual files} from one location to another.
 * <p>
 * A transfer is initiated using {@link VirtualFile#transferTo(VirtualFile)}.
 */
public class Transfer {

    /**
     * Contains the max number of files to be copied / moved interactively.
     */
    private static final int MAX_FILES_THRESHOLD = 128;

    /**
     * Contains the max total size (in bytes) of files which can be copied / Moved interactively.
     */
    private static final long MAX_SIZE_THRESHOLD = 32_768L * 1024L;

    private final VirtualFile source;
    private final VirtualFile destination;
    private boolean forceWrite = true;
    private ProcessContext processContext;
    private boolean autobatch = false;

    @Part
    private static Jobs jobs;

    protected Transfer(VirtualFile source, VirtualFile destination) {
        this.source = source;
        this.destination = destination;
    }

    /**
     * Determines if moving the file to the destination could be performed interactive.
     * <p>
     * For interactive moves, we have an upper limit for the number of files to process and also their size.
     *
     * @return <tt>true</tt> if the file can be moved interactively
     * @see #MAX_FILES_THRESHOLD
     * @see #MAX_SIZE_THRESHOLD
     */
    public boolean canMoveInteractive() {
        return source.canFastMoveTo(destination) || canCopyInteractive();
    }

    /**
     * Determines if a fast move can be performed.
     * <p>
     * A fast move is supported by the underlying implementation and won't juggle any bytes but rather perform a very
     * efficient implementation. A {@link Blob} of layer 2 for example will only update its directory field if moved
     * within the same space.
     *
     * @return <tt>true</tt> if a fast and efficient move is possible, <tt>false</tt> otherwise
     */
    public boolean canFastMove() {
        return source.canFastMoveTo(destination) || canCopyInteractive();
    }

    /**
     * Determines if copying the file to the destination could be performed interactive.
     * <p>
     * For interactive copying, we have an upper limit for the number of files to process and also their size.
     *
     * @return <tt>true</tt> if the file can be moved interactively
     * @see #MAX_FILES_THRESHOLD
     * @see #MAX_SIZE_THRESHOLD
     */
    public boolean canCopyInteractive() {
        AtomicLong countOfFiles = new AtomicLong();
        AtomicLong sizeSum = new AtomicLong();
        source.tree().limit(MAX_FILES_THRESHOLD + 1).iterate(file -> {
            countOfFiles.incrementAndGet();
            return sizeSum.addAndGet(file.size()) < MAX_SIZE_THRESHOLD;
        });

        return countOfFiles.get() < MAX_FILES_THRESHOLD && sizeSum.get() < MAX_SIZE_THRESHOLD;
    }

    /**
     * Notifies the transfer that a batch/process context is available.
     * <p>
     * As soon as a process context is available no limits are enforced anymore (e.g. {@link #canMoveInteractive()}.
     * Also, we provide some metrics and debug messages.
     *
     * @param processContext the process context to execute the transfer within
     * @return the transfer helper itself for fluent method calls
     */
    @CheckReturnValue
    public Transfer batch(ProcessContext processContext) {
        this.processContext = processContext;
        return this;
    }

    /**
     * Signals that a process for the requested operation should be started if it isn't eligible for interactive
     * execution.
     *
     * @return the transfer helper itself for fluent method calls
     */
    @CheckReturnValue
    public Transfer autobatch() {
        this.autobatch = true;
        return this;
    }

    /**
     * Enables smart transfers for copy operations.
     * <p>
     * Using this approach a copy will only happen if the source and destination sizes don't match or if the source
     * is newer than the destination. Otherwise, the operation is skipped.
     *
     * @return the transfer helper itself for fluent method calls
     */
    @CheckReturnValue
    public Transfer smartTransfer() {
        this.forceWrite = false;
        return this;
    }

    /**
     * Manually attempts a fast move.
     * <p>
     * Note that most probably calling {@link #move()} and letting the framework handle everything else is wiser.
     *
     * @return <tt>true</tt> if the fast move succeeded, <tt>false</tt> otherwise
     */
    public boolean tryFastMove() {
        return source.tryFastMoveTo(destination);
    }

    /**
     * Requests to move the source to/into the destination.
     * <p>
     * If the source is too large and cannot be moved interactively, one of three things will happen:
     * <ul>
     *     <li>
     *         A process context has been attached via {@link #batch(ProcessContext)} and the operation will succeed.
     *     </li>
     *     <li>
     *        Auto batching has been enabled via {@link #autobatch()} and a new process will be created and its ID
     *        will be returned.
     *     </li>
     *     <li>
     *        If neither a process is available nor one can be created, an exception is thrown.
     *     </li>
     * </ul>
     *
     * @return a process ID if a background process has been created to perform the operation. An empty optional if the
     * operation was either handled interactively or if a process context was already available.
     */
    public Optional<String> move() {
        if (source.tryFastMoveTo(destination)) {
            return Optional.empty();
        }

        if (processContext == null && !canMoveInteractive()) {
            if (autobatch) {
                return createTransferFilesJob(true);
            } else {
                throw Exceptions.handle()
                                .to(StorageUtils.LOG)
                                .withSystemErrorMessage(
                                        "Layer 3/VFS: Failed to move '%s' to '%s': Contents too large to move interactively!",
                                        source,
                                        destination)
                                .handle();
            }
        }
        transfer(true);
        return Optional.empty();
    }

    private Optional<String> createTransferFilesJob(boolean delete) {
        return Optional.of(jobs.findFactory(TransferFilesJob.NAME, TransferFilesJob.class)
                               .startInBackground(determineParameterValue(delete ?
                                                                          TransferFilesJob.TransferMode.MOVE :
                                                                          TransferFilesJob.TransferMode.COPY)));
    }

    /**
     * Requests to copy  the source to/into the destination.
     * <p>
     * If the source is too large and cannot be moved interactively, one of three things will happen:
     * <ul>
     *     <li>
     *         A process context has been attached via {@link #batch(ProcessContext)} and the operation will succeed.
     *     </li>
     *     <li>
     *        Auto batching has been enabled via {@link #autobatch()} and a new process will be created and its ID
     *        will be returned.
     *     </li>
     *     <li>
     *        If neither a process is available nor one can be created, an exception is thrown.
     *     </li>
     * </ul>
     *
     * @return a process ID if a background process has been created to perform the operation. An empty optional if the
     * operation was either handled interactively or if a process context was already available.
     */
    public Optional<String> copy() {
        if (processContext == null && !canCopyInteractive()) {
            if (autobatch) {
                return createTransferFilesJob(false);
            } else {
                throw Exceptions.handle()
                                .to(StorageUtils.LOG)
                                .withSystemErrorMessage(
                                        "Layer 3/VFS: Failed to copy '%s' to '%s': Contents too large to copy interactively!",
                                        source,
                                        destination)
                                .handle();
            }
        }

        transfer(false);
        return Optional.empty();
    }

    /**
     * Requests to move the children of the source directory into the destination directory.
     * <p>
     * If the children to be moved are large, the same rules as for {@link #move()} apply.
     *
     * @return a process ID if a background process has been created to perform the operation. An empty optional if the
     * operation was either handled interactively or if a process context was already available.
     */
    @CheckReturnValue
    public Optional<String> moveContents() {
        if (processContext == null && !canMoveInteractive()) {
            if (autobatch) {
                return createTransferDirectoryContentsJob(true);
            } else {
                throw Exceptions.handle()
                                .to(StorageUtils.LOG)
                                .withSystemErrorMessage(
                                        "Layer 3/VFS: Failed to move contents of '%s' into '%s': Contents too large to move interactively!",
                                        source,
                                        destination)
                                .handle();
            }
        }
        transferContents(true);
        return Optional.empty();
    }

    private Optional<String> createTransferDirectoryContentsJob(boolean delete) {
        return Optional.of(jobs.findFactory(TransferFilesJob.NAME, TransferFilesJob.class)
                               .startInBackground(determineParameterValue(delete ?
                                                                          TransferFilesJob.TransferMode.MOVE_CONTENTS :
                                                                          TransferFilesJob.TransferMode.COPY_CONTENTS)));
    }

    private Function<String, Value> determineParameterValue(TransferFilesJob.TransferMode mode) {
        return param -> {
            return switch (param) {
                case TransferFilesJob.SOURCE_PARAMETER_NAME -> Value.of(source.path());
                case TransferFilesJob.DESTINATION_PARAMETER_NAME -> Value.of(destination.path());
                case TransferFilesJob.MODE_PARAMETER_NAME -> Value.of(mode.name());
                case TransferFilesJob.SMART_TRANSFER_PARAMETER_NAME -> Value.of(!forceWrite);
                default -> Value.EMPTY;
            };
        };
    }

    /**
     * Requests to copy the children of the source directory into the destination directory.
     * <p>
     * If the children to be moved are large, the same rules as for {@link #copy()} apply.
     *
     * @return a process ID if a background process has been created to perform the operation. An empty optional if the
     * operation was either handled interactively or if a process context was already available.
     */
    @CheckReturnValue
    public Optional<String> copyContents() {
        if (processContext == null && !canCopyInteractive()) {
            if (autobatch) {
                return createTransferDirectoryContentsJob(true);
            } else {
                throw Exceptions.handle()
                                .to(StorageUtils.LOG)
                                .withSystemErrorMessage(
                                        "Layer 3/VFS: Failed to copy contents of '%s' into '%s': Contents too large to copy interactively!",
                                        source,
                                        destination)
                                .handle();
            }
        }
        transferContents(false);
        return Optional.empty();
    }

    private void transfer(boolean delete) {
        if (source.isFile()) {
            if (destination.isDirectory()) {
                // Transfer source file to target directory...
                transferFileTo(source, destination.findChild(source.name()), delete);
            } else {
                // Transfer source file to target file...
                transferFileTo(source, destination, delete);
            }
            if (delete) {
                source.delete();
            }
        } else {
            // Transfer directory into another directory...
            if (!destination.isDirectory()) {
                destination.createAsDirectory();
            }

            // Transfer directory and delete all files and directories including the source directory at the source
            // location if the action is a move.
            transferDirectory(source, destination.findChild(source.name()), delete, delete);
        }
    }

    private void transferDirectory(VirtualFile sourceDirectory,
                                   VirtualFile destinationDirectory,
                                   boolean deleteContent,
                                   boolean deleteSourceDirectory) {
        if (!destinationDirectory.isDirectory()) {
            destinationDirectory.createAsDirectory();
        }

        sourceDirectory.allChildren().stream().forEach(child -> {
            if (child.isFile()) {
                transferFileTo(child, destinationDirectory.findChild(child.name()), deleteContent);
            } else {
                // Transfer and delete the child directory in case content should be deleted.
                transferDirectory(child, destinationDirectory.findChild(child.name()), deleteContent, deleteContent);
            }
            if (deleteContent) {
                child.delete();
            }
        });
        if (deleteSourceDirectory) {
            sourceDirectory.delete();
        }
    }

    protected void transferFileTo(VirtualFile sourceFile, VirtualFile destinationFile, boolean forceTransfer) {
        if (!forceTransfer && !shouldTransfer(sourceFile, destinationFile)) {
            return;
        }

        for (Attempt attempt : Attempt.values()) {
            try (InputStream input = sourceFile.createInputStream()) {
                destinationFile.consumeStream(input, sourceFile.size());
                return;
            } catch (Exception exception) {
                if (attempt.shouldThrow(exception)) {
                    throw Exceptions.handle()
                                    .to(StorageUtils.LOG)
                                    .error(exception)
                                    .withSystemErrorMessage(
                                            "Layer 3/VFS: An error occurred when transferring '%s' to '%s': %s (%s)",
                                            sourceFile.path(),
                                            destinationFile.path())
                                    .handle();
                }
            }
        }
    }

    protected boolean shouldTransfer(VirtualFile sourceFile, VirtualFile destinationFile) {
        if (forceWrite) {
            return true;
        }

        if (!destinationFile.exists()) {
            return true;
        }

        if (sourceFile.size() != destinationFile.size()) {
            return false;
        }

        return sourceFile.lastModified() > 0 && sourceFile.lastModified() >= destinationFile.lastModified();
    }

    private void transferContents(boolean delete) {
        if (!source.isDirectory()) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .withSystemErrorMessage(
                                    "Layer 3/VFS: Cannot transfer contents from '%s' to '%s': Source directory doesn't exist!",
                                    source.path(),
                                    destination.path())
                            .handle();
        }

        if (!destination.isDirectory()) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .withSystemErrorMessage(
                                    "Layer 3/VFS: Cannot transfer contents from '%s' to '%s': Destination directory doesn't exist!",
                                    source.path(),
                                    destination.path())
                            .handle();
        }

        // Transfer content of the directory but don't delete the source directory.
        transferDirectory(source, destination, delete, false);
    }
}
