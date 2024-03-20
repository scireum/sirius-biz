/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.presets;

import sirius.biz.jobs.JobConfigData;
import sirius.biz.process.PersistencePeriod;
import sirius.biz.web.BizController;
import sirius.biz.web.TenantAware;
import sirius.db.mixing.BaseEntity;
import sirius.kernel.commons.Json;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.services.InternalService;
import sirius.web.services.JSONStructuredOutput;

import java.util.Set;

/**
 * Provides the database independent part for the controller which is responsible for managing job presets.
 *
 * @param <P> the generic entity type on which the actual controller will operate on
 */
public abstract class JobPresetsController<P extends BaseEntity<?> & JobPreset> extends BizController {

    private static final String PARAM_JOB_FACTORY = "jobFactory";
    private static final String PARAM_PRESET = "preset";
    private static final String PARAM_PRESET_NAME = "presetName";
    private static final String PARAM_CUSTOM_PERSISTENCE_PERIOD = "customPersistencePeriod";

    private static final Set<String> IGNORED_PARAMETERS =
            Set.of(PARAM_JOB_FACTORY, PARAM_PRESET_NAME, PARAM_CUSTOM_PERSISTENCE_PERIOD, "CSRFToken", "updateOnly");

    private static final String RESPONSE_PARAMS = "params";
    private static final String RESPONSE_PARAM = "param";
    private static final String RESPONSE_NAME = "name";
    private static final String RESPONSE_VALUE = "value";

    /**
     * Returns the entity class being used by this controller.
     *
     * @return the entity class being used by this controller.
     */
    protected abstract Class<P> getPresetType();

    /**
     * Updates or creates the preset for the submitted job and name.
     * <p>
     * This will create or update the job preset with the given name (and job factory) for the current tenant.
     *
     * @param webContext the request to handle
     * @param output     the JSON response to populate (in this case no actual output is expected)
     * @throws Exception in case of an error when populating the entity
     */
    @SuppressWarnings("unchecked")
    @Routed("/jobs/preset/create")
    @InternalService
    public void create(WebContext webContext, JSONStructuredOutput output) throws Exception {
        P preset = (P) mixing.getDescriptor(getPresetType())
                             .getMapper()
                             .select(getPresetType())
                             .eq(TenantAware.TENANT, tenants.getRequiredTenant())
                             .eq(JobPreset.JOB_CONFIG_DATA.inner(JobConfigData.JOB),
                                 webContext.get(PARAM_JOB_FACTORY).asString())
                             .eq(JobPreset.JOB_CONFIG_DATA.inner(JobConfigData.LABEL),
                                 webContext.get(PARAM_PRESET_NAME).asString())
                             .queryFirst();
        if (preset == null) {
            preset = getPresetType().getDeclaredConstructor().newInstance();
            preset.getJobConfigData().setJob(webContext.get(PARAM_JOB_FACTORY).asString());
            preset.getJobConfigData().setLabel(webContext.get(PARAM_PRESET_NAME).asString());
            preset.fillWithCurrentTenant();
        } else {
            preset.getJobConfigData().getConfigMap().clear();
        }

        preset.getJobConfigData()
              .setCustomPersistencePeriod(webContext.get(PARAM_CUSTOM_PERSISTENCE_PERIOD)
                                                    .getEnum(PersistencePeriod.class)
                                                    .orElse(null));

        for (String parameter : webContext.getParameterNames()) {
            if (!IGNORED_PARAMETERS.contains(parameter)) {
                preset.getJobConfigData().getConfigMap().put(parameter, webContext.getParameters(parameter));
            }
        }

        mixing.getDescriptor(getPresetType()).getMapper().update(preset);
    }

    /**
     * Outputs the stored configuration (parameters) for the requested job preset.
     *
     * @param webContext the request to handle
     * @param output     the JSON response to populate
     */
    @Routed("/jobs/preset/load")
    @InternalService
    public void load(WebContext webContext, JSONStructuredOutput output) {
        output.beginArray(RESPONSE_PARAMS);
        P preset = mixing.getDescriptor(getPresetType())
                         .getMapper()
                         .find(getPresetType(), webContext.get(PARAM_PRESET).asString())
                         .orElse(null);
        if (preset != null) {
            assertTenant(preset);
            preset.getJobConfigData().getConfigMap().forEach((name, values) -> {
                output.beginObject(RESPONSE_PARAM);
                output.property(RESPONSE_NAME, name);

                if (values.isEmpty()) {
                    output.property(RESPONSE_VALUE, null);
                } else if (values.size() == 1) {
                    output.property(RESPONSE_VALUE, values.get(0));
                } else {
                    output.property(RESPONSE_VALUE, Json.createArray(values));
                }

                output.endObject();
            });
        }
        output.endArray();
    }

    /**
     * Deletes the requested job preset.
     *
     * @param webContext the request to handle
     * @param output     the JSON response to populate (in this case no actual output is expected)
     */
    @Routed("/jobs/preset/delete")
    @InternalService
    public void delete(WebContext webContext, JSONStructuredOutput output) {
        if (webContext.isSafePOST()) {
            P preset = mixing.getDescriptor(getPresetType())
                             .getMapper()
                             .find(getPresetType(), webContext.get(PARAM_PRESET).asString())
                             .orElse(null);
            if (preset != null) {
                assertTenant(preset);
                preset.getMapper().delete(preset);
            }
        }
    }
}
