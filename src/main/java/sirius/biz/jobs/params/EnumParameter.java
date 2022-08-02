/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.params;

import sirius.kernel.commons.Value;
import sirius.kernel.nls.NLS;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Provides the selection of an enum constant as parameter.
 *
 * @param <E> the enum type to select from
 */
public class EnumParameter<E extends Enum<E>> extends ParameterBuilder<E, EnumParameter<E>> {

    private final Class<E> type;
    private E defaultValue;
    private List<E> values;

    /**
     * Creates a new parameter with the given name, label and enum type.
     *
     * @param name  the name of the parameter
     * @param label the label of the parameter, which will be {@link NLS#smartGet(String) auto translated}
     * @param type  the enum type represented by this parameter
     */
    public EnumParameter(String name, String label, Class<E> type) {
        super(name, label);
        this.type = type;
        this.values = Arrays.asList(type.getEnumConstants());
    }

    /**
     * Specifies the default value to use.
     *
     * @param defaultValue the default value to use
     * @return the parameter itself for fluent method calls
     */
    public EnumParameter<E> withDefault(E defaultValue) {
        this.defaultValue = defaultValue;
        return this;
    }

    /**
     * Specifies the actual list of possible values to select.
     * <p>
     * By default, all enum values are offered.
     *
     * @param values the list of values to select
     * @return the parameter itself for fluent method calls
     */
    public EnumParameter<E> withCustomValues(List<E> values) {
        this.values = new ArrayList<>(values);
        return this;
    }

    /**
     * Enumerates all values provided by the enum.
     *
     * @return the list of value defined by the enum type
     */
    public List<E> getValues() {
        return Collections.unmodifiableList(values);
    }

    @Override
    public String getTemplateName() {
        return "/templates/biz/jobs/params/enum.html.pasta";
    }

    @Override
    protected String checkAndTransformValue(Value input) {
        if (input.isNull()) {
            return defaultValue != null ? defaultValue.name() : null;
        }

        return input.getEnum(type).map(E::name).orElse(null);
    }

    @Override
    public Optional<?> updateValue(Map<String, String> ctx) {
        return updater.apply(ctx).map(value -> {
            Map<String, String> map = new HashMap<>();
            map.put("value", value.name());
            map.put("text", value.toString());
            return map;
        });
    }

    @Override
    protected Optional<E> resolveFromString(Value input) {
        return input.getEnum(type);
    }
}
