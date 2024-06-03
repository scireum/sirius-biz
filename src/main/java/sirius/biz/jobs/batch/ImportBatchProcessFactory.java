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
    public static final Parameter<String> DISPATCHER_PARAMETER = createDispatcherParameter();

    @Part
    private ScriptableEvents scriptableEvents;

    /**
     * Creates a new instance of the dispatcher parameter.
     *
     * @return a new instance of the dispatcher parameter
     */
    public static Parameter<String> createDispatcherParameter() {
        SelectStringParameter dispatcherParameter =
                new SelectStringParameter("eventDispatcher", "$ImportBatchProcessFactory.eventDispatcher");
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
        dispatcherParameter.hideWhen((parameter, context) -> {
            return parameter.getValues().size() < 2;
        });

        return dispatcherParameter.build();
    }

    /**
     * Determines if scriptable events are enabled for this factory.
     * <p>
     * Disabled by default. Override this method to enable scriptable events where needed.
     *
     * @return <tt>true</tt> if scriptable events should be enabled, <tt>false</tt> otherwise
     */
    protected boolean enableScriptableEvents() {
        return false;
    }

    @Override
    protected abstract ImportJob createJob(ProcessContext process);

    @Override
    protected void collectParameters(Consumer<Parameter<?>> parameterCollector) {
        if (enableScriptableEvents() && !scriptableEvents.fetchDispatchersForCurrentTenant().isEmpty()) {
            parameterCollector.accept(createDispatcherParameter());
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
