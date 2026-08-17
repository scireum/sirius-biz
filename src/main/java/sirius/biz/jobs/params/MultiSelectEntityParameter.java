/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.params;

import sirius.db.mixing.BaseEntity;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.nls.NLS;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Provides a base class to implement multi select autocomplete parameters for {@link BaseEntity entities}.
 * <p>
 * In contrast to {@link MultiSelectStringParameter} this doesn't operate on a fixed list of selectable values
 * but rather uses an autocomplete (either an {@link Autocompleter} or a custom autocomplete URI) to determine
 * the selectable entities.
 *
 * @param <V> the type of entities selectable by this parameter
 * @param <P> recursive type reference to support fluent method calls
 */
public abstract class MultiSelectEntityParameter<V extends BaseEntity<?>, P extends MultiSelectEntityParameter<V, P>>
        extends MultiSelectParameter<V, P> {

    protected final EntityParameterUtil<V> entityParameterUtil = new EntityParameterUtil<>(this::getType,
                                                                                           this::getAutocompleter,
                                                                                           this::getCustomAutocompleteUri,
                                                                                           this::getLabel);

    /**
     * Creates a new parameter with the given name and label.
     *
     * @param name  the name of the parameter
     * @param label the label of the parameter, which will be {@link NLS#smartGet(String) auto translated}
     */
    protected MultiSelectEntityParameter(String name, String label) {
        super(name, label);
    }

    /**
     * Creates a new parameter with the given name.
     *
     * @param name the name of the parameter
     */
    protected MultiSelectEntityParameter(String name) {
        super(name, "");
    }

    @Override
    public String getLabel() {
        if (Strings.isEmpty(label)) {
            return entityParameterUtil.getDescriptor().getPluralLabel();
        }

        return super.getLabel();
    }

    /**
     * The type of {@link Autocompleter} to use for this parameter.
     * <p>
     * Note that this autocompleter needs to be @{@link sirius.kernel.di.std.Register registered} to be available
     * to the framework.
     *
     * @return the type of autocompleter to use or <tt>null</tt> if there is none or a custom url via
     * {@link #getCustomAutocompleteUri()} is being used.
     */
    @Nullable
    protected Class<? extends Autocompleter<V>> getAutocompleter() {
        return null;
    }

    /**
     * Returns the custom autocompletion URL used to determine suggestions for inputs provided by the user.
     *
     * @return the autocomplete URL used to provide suggestions for user input. Note that this is only needed, if no
     * {@link #getAutocompleter() autocompleter} is provided (which is the preferred way).
     */
    @Nullable
    protected String getCustomAutocompleteUri() {
        return null;
    }

    @Override
    public String getSuggestionUri() {
        return Optional.ofNullable(entityParameterUtil.getAutocompleteUrl()).orElse("");
    }

    /**
     * Returns the type of entities represented by this parameter.
     *
     * @return the type of entities represented by this
     */
    protected abstract Class<V> getType();

    @Override
    public List<MultiSelectValue> getValues(Map<String, String> context) {
        return get(context).orElseGet(Collections::emptyList)
                           .stream()
                           .map(entity -> new MultiSelectValue(createValueName(entity), createValueLabel(entity), true))
                           .toList();
    }

    @Override
    protected String createValueName(V entity) {
        return entity.getIdAsString();
    }

    @Override
    protected String createValueLabel(V entity) {
        return entityParameterUtil.createLabel(entity);
    }

    @Nullable
    @Override
    protected String checkAndTransformSingleValue(Value input) {
        V entity = entityParameterUtil.findEntityOrFail(input);
        if (entity == null) {
            return null;
        }

        assertAccess(entity);
        return entity.getIdAsString();
    }

    @Override
    protected Optional<V> resolveSingleValueFromString(Value input) {
        return entityParameterUtil.findEntity(input);
    }

    /**
     * Checks if the current user may use the given entity as value for this parameter.
     *
     * @param entity the entity to check
     */
    protected void assertAccess(V entity) {
        entityParameterUtil.assertTenantAccess(entity);
    }
}
