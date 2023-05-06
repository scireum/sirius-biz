/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs;

import sirius.biz.jobs.params.FileParameter;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.Processes;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.storage.layer2.Blob;
import sirius.biz.storage.layer2.BlobStorage;
import sirius.biz.storage.layer2.BlobStorageSpace;
import sirius.biz.storage.layer3.SingularVFSRoot;
import sirius.biz.storage.layer3.TmpRoot;
import sirius.biz.storage.layer3.VirtualFileSystem;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.kernel.nls.NLS;
import sirius.web.security.UserContext;

import java.io.OutputStream;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Provides a base class for all roots which sooner or later start a {@link JobFactory job} for an uploaded file.
 */
public abstract class JobStartingRoot extends SingularVFSRoot {

    @Part
    protected BlobStorage blobStorage;

    @Part
    protected VirtualFileSystem virtualFileSystem;

    @Part
    protected Processes processes;

    /**
     * Creates an <tt>OutputStream</tt> which triggers the given job with the given parameters once the stream is closed.
     *
     * @param jobToRun          the job to actually run
     * @param parameterProvider permits to control the parameter values for the job (the file is automatically used as
     *                          first {@link FileParameter} of the job)
     * @param filename          the actual filename of the file being processed
     * @return an output stream which triggers the job once the stream is closed
     */
    protected OutputStream uploadAndTrigger(JobFactory jobToRun,
                                            Function<String, Value> parameterProvider,
                                            String filename) {
        try {
            BlobStorageSpace temporaryStorageSpace = blobStorage.getSpace(TmpRoot.TMP_SPACE);
            Blob buffer = temporaryStorageSpace.createTemporaryBlob(UserContext.getCurrentUser().getTenantId());
            return buffer.createOutputStream(() -> {
                if (buffer.getSize() > 0) {
                    temporaryStorageSpace.markAsUsed(buffer);
                    trigger(jobToRun, parameterProvider, buffer, filename);
                } else {
                    buffer.delete();
                }
            }, filename);
        } catch (Exception e) {
            throw Exceptions.handle(e);
        }
    }

    private void trigger(JobFactory jobToRun, Function<String, Value> parameterProvider, Blob buffer, String filename) {
        processes.executeInStandbyProcessForCurrentTenant(getStandbyProcessType(),
                                                          this::getStandbyProcessDescription,
                                                          ctx -> triggerInProcess(jobToRun,
                                                                                  parameterProvider,
                                                                                  buffer,
                                                                                  filename,
                                                                                  ctx));
    }

    /**
     * Returns the type for the standby process which is used to start the job.
     * <p>
     * Starting jobs happens in a standby process so that we can log any problems and provide some debugging capabilities.
     *
     * @return the type to be used in
     * {@link Processes#executeInStandbyProcessForCurrentTenant(String, Supplier, Consumer)}
     */
    protected abstract String getStandbyProcessType();

    /**
     * Returns the description for the standby process which is used to start the job.
     * <p>
     * Starting jobs happens in a standby process so that we can log any problems and provide some debugging capabilities.
     *
     * @return the description to be used in
     * {@link Processes#executeInStandbyProcessForCurrentTenant(String, Supplier, Consumer)}
     */
    protected abstract String getStandbyProcessDescription();

    private void triggerInProcess(JobFactory jobToRun,
                                  Function<String, Value> parameterProvider,
                                  Blob buffer,
                                  String filename,
                                  ProcessContext processContext) {
        if (processContext.isDebugging()) {
            processContext.debug(ProcessLog.info()
                                           .withFormattedMessage(
                                                   "Starting job '%s' for user '%s' using the uploaded file '%s' (%s')",
                                                   jobToRun.getLabel(),
                                                   UserContext.getCurrentUser().getUserName(),
                                                   buffer.getFilename(),
                                                   NLS.formatSize(buffer.getSize())));
        }

        String parameterName = findFileParameter(jobToRun);

        try {
            jobToRun.startInBackground(param -> {
                if (Strings.areEqual(param, parameterName)) {
                    return Value.of(virtualFileSystem.makePath(TmpRoot.TMP_PATH, buffer.getBlobKey(), filename));
                } else {
                    return parameterProvider.apply(param);
                }
            });
        } catch (HandledException exception) {
            processContext.log(ProcessLog.error()
                                         .withFormattedMessage(
                                                 "Failed to start job '%s' for user '%s' using the uploaded file '%s' (%s'): %s",
                                                 jobToRun.getLabel(),
                                                 UserContext.getCurrentUser().getUserName(),
                                                 buffer.getFilename(),
                                                 NLS.formatSize(buffer.getSize()),
                                                 exception.getMessage()));
        }
    }

    private String findFileParameter(JobFactory jobToRun) {
        return jobToRun.getParameters()
                       .stream()
                       .filter(p -> p.getBuilder() instanceof FileParameter fileParameter
                                    && fileParameter.isFilesOnly())
                       .map(Parameter::getName)
                       .findFirst()
                       .orElse(null);
    }
}
