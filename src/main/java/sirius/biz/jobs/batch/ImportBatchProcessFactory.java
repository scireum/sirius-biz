/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch;

import sirius.biz.cluster.work.DistributedTaskExecutor;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.jobs.params.SelectStringParameter;
import sirius.biz.process.PersistencePeriod;
import sirius.biz.process.ProcessContext;
import sirius.biz.scripting.ScriptableEvents;
import sirius.kernel.di.Injector;
import sirius.kernel.di.std.Part;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Provides a base implementation for batch jobs which are executed by the {@link ImportBatchProcessTaskExecutor}.
 */
public abstract class ImportBatchProcessFactory extends BatchProcessJobFactory {

    /**
     * Provides an executor for batch jobs which are all put in the prioritized queue named "import-jobs".
     */
    public static class ImportBatchProcessTaskExecutor extends BatchProcessTaskExecutor {

        @Override
        public String queueName() {
            return "import-jobs";
        }
    }

    /**
     * Permits selecting the {@link sirius.biz.scripting.ScriptableEventDispatcher} to use for this import.
     */
    public static final Parameter<String> DISPATCHER_PARAMETER;

    static {
        SelectStringParameter dispatcherParameter = new SelectStringParameter("eventDispatcher", "$ImportBatchProcessFactory.eventDispatcher");
        dispatcherParameter.markRequired();
        dispatcherParameter.withDescription("$ImportBatchProcessFactory.eventDispatcher.help");
        dispatcherParameter.withEntriesProvider(() -> {
            Map<String, String> eventDispatchers = new LinkedHashMap<>();
            ScriptableEvents scriptableEvents = Injector.context().getPart(ScriptableEvents.class);
            if (scriptableEvents != null) {
                scriptableEvents.fetchDispatchersForCurrentTenant()
                            .forEach(dispatcher -> eventDispatchers.put(dispatcher, dispatcher));
            }
            return eventDispatchers;
        });

        DISPATCHER_PARAMETER = dispatcherParameter.build();
    }

    @Part
    private ScriptableEvents scriptableEvents;

    @Override
    protected abstract ImportJob createJob(ProcessContext process);

    @Override
    protected void collectParameters(Consumer<Parameter<?>> parameterCollector) {
        if (scriptableEvents.fetchDispatchersForCurrentTenant().size() > 1) {
            parameterCollector.accept(DISPATCHER_PARAMETER);
        }
    }

    @Override
    protected Class<? extends DistributedTaskExecutor> getExecutor() {
        return ImportBatchProcessTaskExecutor.class;
    }

    @Override
    public String getIcon() {
        return "fa-solid fa-upload";
    }

    @Override
    protected PersistencePeriod getPersistencePeriod() {
        return PersistencePeriod.SIX_YEARS;
    }
}
