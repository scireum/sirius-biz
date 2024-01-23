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
import sirius.biz.jobs.params.Parameter;
import sirius.biz.process.PersistencePeriod;
import sirius.biz.process.ProcessContext;
import sirius.biz.storage.util.StorageUtils;
import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Defines a job which deletes files in the background.
 */
public class DeleteFilesJob extends BatchJob {
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

    }

    /**
     * Defines a factory to create new instances of {@link DeleteFilesJob}.
     */
    @Register(framework = StorageUtils.FRAMEWORK_STORAGE)
    public static class DeleteFilesJobFactory extends DefaultBatchProcessFactory {

        @Override
        protected void collectParameters(Consumer<Parameter<?>> parameterCollector) {

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
            return getLabel();
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
