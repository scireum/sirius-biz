/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3;

import sirius.biz.jobs.StandardCategories;
import sirius.biz.jobs.batch.BatchJob;
import sirius.biz.jobs.batch.DefaultBatchProcessFactory;
import sirius.biz.jobs.params.BooleanParameter;
import sirius.biz.jobs.params.FileParameter;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.process.PersistencePeriod;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.storage.util.StorageUtils;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.HandledException;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Defines a job which deletes files in the background.
 */
public class DeleteFilesJob extends BatchJob {

    private static final Parameter<VirtualFile> SOURCE_PATH_PARAMETER =
            new FileParameter("sourcePath", "$DeleteFilesJob.sourcePath").withDescription(
                    "$DeleteFilesJob.sourcePath.help").directoriesOnly().markRequired().build();

    private static final Parameter<Boolean> SIMULATION_PARAMETER =
            new BooleanParameter("simulation", "$DeleteFilesJob.simulation").withDescription(
                    "$DeleteFilesJob.simulation.help").withDefaultTrue().build();

    private static final Parameter<Boolean> DELETE_RECURSIVE_PARAMETER =
            new BooleanParameter("recursive", "$DeleteFilesJob.recursive").withDescription(
                    "$DeleteFilesJob.recursive.help").withDefaultTrue().build();

    private static final String PATH_KEY = "path";

    /**
     * Creates a new batch job for the given batch process.
     * <p>
     * As a batch job is created per execution, subclasses can define fields and fill those from parameters
     * defined by their factory.
     *
     * @param process the context in which the process will be executed
     */
    protected DeleteFilesJob(ProcessContext process) {
        super(process);
    }

    @Override
    public void execute() throws Exception {
        VirtualFile sourcePath = process.require(SOURCE_PATH_PARAMETER);
        if (!sourcePath.exists() || !sourcePath.isDirectory()) {
            process.log(ProcessLog.error()
                                  .withNLSKey("DeleteFilesJob.sourcePath.invalid")
                                  .withContext(PATH_KEY, sourcePath.path()));
            return;
        }
        handleDirectory(sourcePath, process.require(DELETE_RECURSIVE_PARAMETER));
    }

    private boolean handleDirectory(VirtualFile directory, boolean recursive) {
        AtomicBoolean childSkipped = new AtomicBoolean(false);

        if (recursive) {
            directory.allChildren().excludeFiles().subTreeOnly().maxDepth(1).iterate(subDirectory -> {
                if (process.isActive()) {
                    childSkipped.set(!handleDirectory(subDirectory, true));
                } else {
                    childSkipped.set(true);
                }
                return process.isActive();
            });
        }

        directory.allChildren().excludeDirectories().subTreeOnly().maxDepth(1).iterate(file -> {
            if (process.isActive()) {
                childSkipped.set(!handleFile(file));
            } else {
                childSkipped.set(true);
            }
            return process.isActive();
        });

        if (!recursive) {
            return false;
        }

        if (!directory.canDelete()) {
            process.log(ProcessLog.warn()
                                  .withNLSKey("DeleteFilesJob.directory.cannotDelete")
                                  .withContext(PATH_KEY, directory.path()));
            return false;
        }

        if (childSkipped.get()) {
            process.log(ProcessLog.warn()
                                  .withNLSKey("DeleteFilesJob.directory.notEmpty")
                                  .withContext(PATH_KEY, directory.path()));
            return false;
        }

        try {
            if (Boolean.FALSE.equals(process.require(SIMULATION_PARAMETER))) {
                directory.delete();
            }
            process.log(ProcessLog.info()
                                  .withNLSKey("DeleteFilesJob.directory.deleted")
                                  .withContext(PATH_KEY, directory.path()));
        } catch (HandledException exception) {
            process.log(ProcessLog.error().withMessage(exception.getMessage()));
            return false;
        }

        return true;
    }

    private boolean handleFile(VirtualFile file) {
        if (file.readOnly()) {
            process.log(ProcessLog.warn().withNLSKey("DeleteFileJob.file.readOnly").withContext(PATH_KEY, file.path()));
            return false;
        }

        if (!file.canDelete()) {
            process.log(ProcessLog.warn()
                                  .withNLSKey("DeleteFilesJob.file.cannotDelete")
                                  .withContext(PATH_KEY, file.path()));
            return false;
        }

        try {
            if (Boolean.FALSE.equals(process.require(SIMULATION_PARAMETER))) {
                file.delete();
            }
            process.log(ProcessLog.info().withNLSKey("DeleteFilesJob.file.deleted").withContext(PATH_KEY, file.path()));
        } catch (HandledException exception) {
            process.log(ProcessLog.error().withMessage(exception.getMessage()));
            return false;
        }

        return true;
    }

    /**
     * Defines a factory to create new instances of {@link DeleteFilesJob}.
     */
    @Register(framework = StorageUtils.FRAMEWORK_STORAGE)
    public static class DeleteFilesJobFactory extends DefaultBatchProcessFactory {

        @Override
        protected void collectParameters(Consumer<Parameter<?>> parameterCollector) {
            parameterCollector.accept(SOURCE_PATH_PARAMETER);
            parameterCollector.accept(DELETE_RECURSIVE_PARAMETER);
            parameterCollector.accept(SIMULATION_PARAMETER);
        }

        @Override
        public String getCategory() {
            return StandardCategories.MISC;
        }

        @Override
        public String getIcon() {
            return "fa fa-trash";
        }

        @Override
        protected String createProcessTitle(Map<String, String> context) {
            if (SIMULATION_PARAMETER.get(context).orElse(false)) {
                return Strings.apply("%s (%s) - %s",
                                     getLabel(),
                                     SIMULATION_PARAMETER.getLabel(),
                                     context.get(SOURCE_PATH_PARAMETER.getName()));
            }
            return Strings.apply("%s - %s", getLabel(), context.get(SOURCE_PATH_PARAMETER.getName()));
        }

        @Override
        protected PersistencePeriod getPersistencePeriod() {
            return PersistencePeriod.ONE_YEAR;
        }

        @Override
        protected BatchJob createJob(ProcessContext process) throws Exception {
            return new DeleteFilesJob(process);
        }

        @Nonnull
        @Override
        public String getName() {
            return "delete-files";
        }
    }
}
