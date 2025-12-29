/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import sirius.biz.jobs.batch.BatchProcessJobFactory;
import sirius.biz.process.PersistencePeriod;
import sirius.biz.web.Autoloaded;
import sirius.db.mixing.Composite;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.BeforeSave;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.Lob;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.annotations.Transient;
import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Json;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.commons.ValueHolder;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.kernel.health.Log;
import sirius.web.http.WebContext;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents a composite which can be embedded into a {@link sirius.db.mixing.BaseEntity} and contain all relevant
 * data to describe a job and its start parameters.
 */
public class JobConfigData extends Composite {

    private static final String LIST_DELIMITER = "|";

    /**
     * Contains the name of the {@link JobFactory} to launch.
     */
    public static final Mapping JOB = Mapping.named("job");
    @Length(255)
    private String job;

    /**
     * Contains a copy of {@link JobFactory#getLabel()} to quickly render the selected job.
     */
    public static final Mapping JOB_NAME = Mapping.named("jobName");
    @Length(255)
    private String jobName;

    /**
     * Contains an additional label or description for this job.
     */
    public static final Mapping LABEL = Mapping.named("label");
    @Length(255)
    @NullAllowed
    @Autoloaded
    private String label;

    /**
     * Contains a custom persistence period for jobs started via this configuration.
     */
    public static final Mapping CUSTOM_PERSISTENCE_PERIOD = Mapping.named("customPersistencePeriod");
    @NullAllowed
    @Autoloaded
    private PersistencePeriod customPersistencePeriod;

    /**
     * Contains the configuration stored as JSON object.
     */
    public static final Mapping CONFIGURATION = Mapping.named("configuration");
    @Lob
    private String configuration;

    @Transient
    private Map<String, List<String>> configMap;

    @Part
    private static Jobs jobs;

    @Override
    public String toString() {
        if (Strings.isFilled(getLabel())) {
            return getLabel();
        }
        if (Strings.isFilled(getJobName())) {
            return getJobName();
        }

        return getJobFactory().getLabel();
    }

    @BeforeSave
    protected void updateConfig() {
        if (configMap != null) {
            ObjectNode configObject = Json.createObject();
            configMap.forEach((key, values) -> {
                if (values.isEmpty() || Strings.isEmpty(values.get(0))) {
                    configObject.set(key, null);
                } else if (values.size() == 1) {
                    configObject.put(key, values.get(0));
                } else {
                    configObject.putPOJO(key, values);
                }
            });
            configuration = Json.write(configObject);
        }

        if (Strings.isFilled(job)) {
            jobName = getJobFactory().getLabel();
        }
    }

    /**
     * Returns the {@link JobFactory} being used.
     *
     * @return the resolved job factory as specified in {@link #JOB}.
     */
    public JobFactory getJobFactory() {
        try {
            return jobs.findFactory(getJob(), JobFactory.class);
        } catch (IllegalArgumentException exception) {
            throw Exceptions.handle()
                            .to(Log.BACKGROUND)
                            .error(exception)
                            .withNLSKey("JobConfigData.unknownJob")
                            .set("job", job)
                            .handle();
        }
    }

    /**
     * Returns the configuration as raw string map.
     *
     * @return the configuration as mutable map
     */
    @SuppressWarnings("AssignmentOrReturnOfFieldWithMutableType")
    @Explain("This is intentionally mutable.")
    public Map<String, List<String>> getConfigMap() {
        if (configMap == null) {
            configMap = new HashMap<>();
            if (configuration != null) {
                Json.parseObject(configuration).properties().forEach(entry -> {
                    if (entry.getValue().isArray()) {
                        configMap.put(entry.getKey(),
                                      entry.getValue()
                                           .valueStream()
                                           .map(JsonNode::asText)
                                           .filter(Strings::isFilled)
                                           .toList());
                    } else {
                        configMap.put(entry.getKey(), Stream.ofNullable(entry.getValue().asText(null)).toList());
                    }
                });
            }
        }

        return configMap;
    }

    /**
     * Returns the stored configuration as a parameter context map.
     * <p>
     * List types are concatenated using {@link #LIST_DELIMITER} in order to be consumable by the parameters.
     *
     * @return a map containing the parameter context
     */
    public Map<String, String> asParameterContext() {
        return getConfigMap().entrySet()
                             .stream()
                             .collect(Collectors.toMap(Map.Entry::getKey,
                                                       entry -> String.join(LIST_DELIMITER, entry.getValue())));
    }

    /**
     * Returns the parameter value which can be used as provider for {@link JobFactory#startInBackground(Function)}.
     *
     * @param key the parameter value to fetch
     * @return the value for the given parameter wrapped as <tt>Value</tt>
     */
    @Nonnull
    public Value fetchParameter(String key) {
        if (BatchProcessJobFactory.HIDDEN_PARAMETER_CUSTOM_PERSISTENCE_PERIOD.equals(key)) {
            return Value.of(customPersistencePeriod);
        }

        List<String> values = getConfigMap().get(key);

        // Missing parameters -> return null in order to fall back to the default value
        if (values == null) {
            return Value.EMPTY;
        }

        // Intentionally empty parameters -> return an empty string to prevent a fallback to the default value
        if (values.isEmpty()) {
            return Value.of("");
        }

        if (values.size() == 1) {
            return Value.of(values.get(0));
        }

        return Value.of(values);
    }

    /**
     * Reads and validates all parameters from the given web context.
     *
     * @param webContext the request to read the parameter values from
     */
    public void loadFromContext(WebContext webContext) {
        // Check all parameters here to notify the user about config short comings...
        ValueHolder<HandledException> errorHolder = new ValueHolder<>(null);
        Map<String, String> data =
                getJobFactory().buildAndVerifyContext(webContext::get, true, (parameter, exception) -> {
                    if (errorHolder.get() == null) {
                        errorHolder.accept(exception);
                    }
                });

        // ...however, store the original user input here as JobFactory.startInBackground will
        // perform another check and transform itself...
        getConfigMap().clear();
        data.keySet().forEach(key -> getConfigMap().put(key, webContext.getParameters(key)));

        if (errorHolder.get() != null) {
            throw errorHolder.get();
        }
    }

    public String getJob() {
        return job;
    }

    public void setJob(String job) {
        this.job = job;
    }

    public String getJobName() {
        return jobName;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public PersistencePeriod getCustomPersistencePeriod() {
        return customPersistencePeriod;
    }

    public void setCustomPersistencePeriod(PersistencePeriod customPersistencePeriod) {
        this.customPersistencePeriod = customPersistencePeriod;
    }

    public String getConfiguration() {
        return configuration;
    }

    public void setConfiguration(String configuration) {
        this.configuration = configuration;
    }
}
