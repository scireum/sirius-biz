/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.params;

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
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;
import sirius.kernel.nls.NLS;

import java.util.Map;
import java.util.Optional;

/**
 * Provides a base class to implement autocomplete parameters for {@link BaseEntity entities}.
 *
 * @param <V> the type of entities selectable by this parameter
 */
public abstract class EntityParameter<V extends BaseEntity<?>> extends Parameter<V, EntityParameter<V>> {

    @Part
    protected static Mixing mixing;

    @Part
    protected static OMA oma;

    @Part
    protected static Mango mango;

    @Part
    protected static Elastic elastic;

    @Part
    protected static Tenants tenants;

    protected EntityDescriptor descriptor;

    /**
     * Creates a new parameter with the given name and label.
     *
     * @param name  the name of the parameter
     * @param label the label of the parameter, which will be {@link NLS#smartGet(String) auto translated}
     */
    protected EntityParameter(String name, String label) {
        super(name, label);
        this.descriptor = mixing.getDescriptor(getType());
    }

    /**
     * Creates a new parameter with the given name.
     *
     * @param name the name of the parameter
     */
    protected EntityParameter(String name) {
        super(name, "");
        this.descriptor = mixing.getDescriptor(getType());
        withLabel(descriptor.getLabel());
    }

    /**
     * Returns the autocompletion URL used to determine suggestions for inputs provided by the user.
     *
     * @return the autocomplete URL used to provide suggestions for user input
     */
    public abstract String getAutocompleteUri();

    /**
     * Returns the type of entities represented by this paramter.
     *
     * @return the type of entities represented by this
     */
    protected abstract Class<V> getType();

    @SuppressWarnings("unchecked")
    protected BaseMapper<V, ?, ?> getMapper() {
        return (BaseMapper<V, ?, ?>) descriptor.getMapper();
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

    protected String createLabel(V entity) {
        return entity.toString();
    }

    @Override
    public String getTemplateName() {
        return "/templates/jobs/params/entity-autocomplete.html.pasta";
    }

    @Override
    protected Optional<V> resolveFromString(Value input) {
        return getMapper().find(getType(), input.get());
    }

    @Override
    protected String checkAndTransformValue(Value input) {
        V entity = getMapper().find(getType(), input.get()).orElse(null);
        if (entity == null) {
            return null;
        }
        if (entity instanceof TenantAware) {
            tenants.assertTenant((TenantAware) entity);
        }
        if (!checkAccess(entity)) {
            return null;
        }

        return entity.getIdAsString();
    }

    /**
     * Determines if the current user may use the given entity as value for this parameter.
     *
     * @param entity the entity to check
     * @return <tt>true</tt> if the entity may be used as parameter value, <tt>false otherwise</tt>
     */
    @SuppressWarnings("squid:S1172")
    @Explain("Parameter may be used by subclasses")
    protected boolean checkAccess(V entity) {
        return true;
    }
}
