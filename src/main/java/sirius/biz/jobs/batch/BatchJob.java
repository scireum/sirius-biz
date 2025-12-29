/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch;

import sirius.biz.process.ProcessContext;
import sirius.biz.scripting.ScriptableEventHandler;
import sirius.biz.storage.layer1.FileHandle;
import sirius.biz.storage.layer3.VirtualFile;
import sirius.kernel.commons.Streams;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Provides a base class for jobs executed by subclasses of {@link BatchProcessJobFactory}.
 * <p>
 * As factories are static, jobs can be thought of parameter objects which are created for each job execution
 * and therefore carry all the state required for it.
 */
public abstract class BatchJob implements Closeable {

    protected ProcessContext process;
    protected ScriptableEventHandler eventHandler = new ScriptableEventHandler();

    /**
     * Creates a new batch job for the given batch process.
     * <p>
     * As a batch job is created per execution, subclasses can define fields and fill those from parameters
     * defined by their factory.
     *
     * @param process the context in which the process will be executed
     */
    protected BatchJob(ProcessContext process) {
        this.process = process;
    }

    /**
     * Attaches the given file to the surrounding process.
     * <p>
     * This can be used e.g. to persist input data for import jobs.
     *
     * @param file the file to attach
     */
    protected void attachFile(VirtualFile file) {
        try (InputStream in = file.createInputStream(); OutputStream out = process.addFile(file.name())) {
            Streams.transfer(in, out);
        } catch (IOException exception) {
            process.handle(exception);
        }
    }

    /**
     * Attaches the given file handle to the surrounding process.
     * <p>
     * This can be used e.g. to persist input data for import jobs.
     *
     * @param filename the name of the file to attach
     * @param file     the file to attach
     */
    protected void attachFile(String filename, FileHandle file) {
        try (InputStream in = file.getInputStream(); OutputStream out = process.addFile(filename)) {
            Streams.transfer(in, out);
        } catch (IOException exception) {
            process.handle(exception);
        }
    }

    /**
     * Initializes the event dispatchers for this job.
     * <p>
     * This information is provided by {@link BatchProcessJobFactory#enableScriptableEvents()}.
     * <p>
     * Note that this method is always invoked, so jobs can override it to perform custom actions upon initialization.
     *
     * @param enabled indicates whether event dispatchers should be initialized at all.
     */
    protected void initializeEventDispatchers(boolean enabled) {
        if (enabled) {
            eventHandler.initializeEventDispatchers();
        }
    }

    /**
     * Performs the execution of the task.
     * <p>
     * As a batch job is created per execution, this method can branch into several other methods to keep the whole
     * process maintainable.
     *
     * @throws Exception in case on an error during execution
     */
    public abstract void execute() throws Exception;

    /**
     * Invoked independently of the outcome of {@link #execute()} to perform required cleanups.
     *
     * @throws IOException inherited by {@link Closeable} can be used to signal an error of any kind
     */
    @Override
    public void close() throws IOException {
        // may be overwritten by subclasses
    }
}
