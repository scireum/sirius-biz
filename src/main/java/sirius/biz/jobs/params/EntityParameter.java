/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.params;

import com.alibaba.fastjson.JSONObject;
import sirius.biz.tenants.Tenants;
import sirius.biz.web.TenantAware;
import sirius.db.es.Elastic;
import sirius.db.jdbc.OMA;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.BaseMapper;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.Mixing;
import sirius.db.mongo.Mango;
import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Value;
import sirius.kernel.di.Injector;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;

/**
 * Provides a base class to implement autocomplete parameters for {@link BaseEntity entities}.
 *
 * @param <V> the type of entities selectable by this parameter
 * @param <P> recursive type reference to support fluent method calls
 */
public abstract class EntityParameter<V extends BaseEntity<?>, P extends EntityParameter<V, P>>
        extends ParameterBuilder<V, P> {

    @Part
    protected static Mixing mixing;

    @Part
    protected static OMA oma;

    @Part
    protected static Mango mango;

    @Part
    protected static Elastic elastic;

    @Part
    @Nullable
    protected static Tenants<?, ?, ?> tenants;

    private EntityDescriptor descriptor;

    /**
     * Creates a new parameter with the given name and label.
     *
     * @param name  the name of the parameter
     * @param label the label of the parameter, which will be {@link NLS#smartGet(String) auto translated}
     */
    protected EntityParameter(String name, String label) {
        super(name, label);
    }

    /**
     * Creates a new parameter with the given name.
     *
     * @param name the name of the parameter
     */
    protected EntityParameter(String name) {
        super(name, "");
    }

    @Override
    public String getLabel() {
        if (Strings.isEmpty(label)) {
            return getDescriptor().getLabel();
        }

        return super.getLabel();
    }

    /**
     * Returns the autocompletion URL used to determine suggestions for inputs provided by the user.
     *
     * @return the autocomplete URL used to provide suggestions for user input
     * @deprecated override {@link #getAutocompleterName()} instead, or, if it is not feasible to implement an
     * {@link Autocompleter}, override {@link #getAutocompleteUrl()}
     */
    @Deprecated
    public String getAutocompleteUri() {
        return null;
    }

    /**
     * The {@link sirius.kernel.di.std.Register registered} name of an {@link Autocompleter} implementation.
     *
     * @return the name of the Autocompleter
     */
    public String getAutocompleterName() {
        return null;
    }

    /**
     * Returns the autocompletion URL used to determine suggestions for inputs provided by the user.
     * <p>
     * Consider to register an {@link Autocompleter} instead.
     *
     * @return the autocomplete URL used to provide suggestions for user input
     */
    public String getAutocompleteUrl() {
        return Optional.ofNullable(getAutocompleterName())
                       .map(name -> "/jobs/parameter-autocomplete/" + name)
                       .orElseGet(this::getAutocompleteUri);
    }

    /**
     * Returns the type of entities represented by this paramter.
     *
     * @return the type of entities represented by this
     */
    protected abstract Class<V> getType();

    /**
     * Returns the mapper which is to be used to the entity represented by this parameter.
     *
     * @return the mapper of the represented entity
     */
    protected BaseMapper<V, ?, ?> getMapper() {
        return getDescriptor().getMapper();
    }

    /**
     * Converts a selected value into an id and label to be shown in the editor.
     *
     * @param context the parameter values used to read the selected value from
     * @return a tuple containing the id and label of the selected value or <tt>null</tt> if no valid value is selected
     */
    public Tuple<String, String> renderCurrentValue(Map<String, String> context) {
        V entity = get(context).orElse(null);
        if (entity == null) {
            return null;
        }

        return Tuple.create(entity.getIdAsString(), createLabel(entity));
    }

    /**
     * Derives a label to show for a selected entity.
     *
     * @param entity the entity to derive  the label from
     * @return the label or textual representation to use for the given entity
     */
    @SuppressWarnings("unchecked")
    @Explain("There is no way to rewrite this. "
             + "Also, it _should_ be fine if the Autocompleter is implemented according to the specification")
    protected String createLabel(V entity) {
        if (Strings.isFilled(getAutocompleterName())) {
            Autocompleter<V> autocompleter = Injector.context().getPart(getAutocompleterName(), Autocompleter.class);
            return autocompleter.toLabel(entity);
        }
        return entity.toString();
    }

    @Override
    public Optional<?> computeValueUpdate(Map<String, String> parameterContext) {
        return updater.apply(parameterContext)
                      .map(value -> new JSONObject().fluentPut("value", value.getIdAsString())
                                                    .fluentPut("text", createLabel(value)));
    }

    @Override
    public String getTemplateName() {
        return "/templates/biz/jobs/params/entity-autocomplete.html.pasta";
    }

    @Override
    protected Optional<V> resolveFromString(Value input) {
        return getMapper().find(getType(), input.get());
    }

    @Override
    protected String checkAndTransformValue(Value input) {
        V entity = getMapper().find(getType(), input.get()).orElse(null);

        if (entity == null) {
            if (input.isFilled()) {
                throw Exceptions.createHandled()
                                .withNLSKey("Parameter.invalidValue")
                                .set("name", getLabel())
                                .set("message", NLS.get("EntityParameter.mustExist"))
                                .handle();
            }
            return null;
        }

        assertAccess(entity);

        return entity.getIdAsString();
    }

    /**
     * Checks if the current user may use the given entity as value for this parameter.
     *
     * @param entity the entity to check
     */
    protected void assertAccess(V entity) {
        if (entity instanceof TenantAware tenantAware) {
            tenants.assertTenant(tenantAware);
        }
    }

    /**
     * Determines the {@link EntityDescriptor} for the
     * set {@link #getType() parameter type}.
     *
     * @return the entity descriptor for the parameter type
     */
    protected EntityDescriptor getDescriptor() {
        if (descriptor == null) {
            descriptor = mixing.getDescriptor(getType());
        }

        return descriptor;
    }
}
