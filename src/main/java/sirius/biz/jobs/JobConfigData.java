/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs;

import com.alibaba.fastjson.JSON;
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
import java.util.Map;
import java.util.function.Function;

/**
 * Represents a composite which can be embedded into a {@link sirius.db.mixing.BaseEntity} and contain all relevant
 * data to describe a job and its start parameters.
 */
public class JobConfigData extends Composite {

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
    private Map<String, String> configMap;

    @Part
    private static Jobs jobs;

    @BeforeSave
    protected void updateConfig() {
        if (configMap != null) {
            configuration = JSON.toJSONString(configMap);
        }

        if (Strings.isFilled(job)) {
            jobName = getJobFactory().getName();
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
        } catch (IllegalArgumentException e) {
            throw Exceptions.handle()
                            .to(Log.BACKGROUND)
                            .error(e)
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
    public Map<String, String> getConfigMap() {
        if (configMap == null) {
            configMap = new HashMap<>();
            if (configuration != null) {
                JSON.parseObject(configuration)
                    .forEach((key, value) -> configMap.put(key, value == null ? null : value.toString()));
            }
        }

        return configMap;
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
        } else {
            return Value.of(getConfigMap().get(key));
        }
    }

    /**
     * Reads and validates all parameters from the given web context.
     *
     * @param ctx the request to read the parameter values from
     */
    public void loadFromContext(WebContext ctx) {
        // Check all parameters here to notify the user about config short comings...
        ValueHolder<HandledException> errorHolder = new ValueHolder<>(null);
        Map<String, String> data = getJobFactory().buildAndVerifyContext(ctx::get, true, (p, ex) -> {
            if (errorHolder.get() == null) {
                errorHolder.accept(ex);
            }
        });

        // ...however, store the original user input here as JobFactory.startInBackground will
        // perform another check and transform itself...
        getConfigMap().clear();
        data.keySet().forEach(key -> getConfigMap().put(key, ctx.getParameter(key)));

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
}
