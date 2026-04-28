/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.params;

import sirius.kernel.commons.Json;
import sirius.kernel.commons.Value;
import sirius.kernel.nls.NLS;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Provides the selection of an enum constant as parameter.
 *
 * @param <E> the enum type to select from
 */
public class EnumParameter<E extends Enum<E>> extends ParameterBuilder<E, EnumParameter<E>> {

    private final Class<E> type;
    private E defaultValue;
    private Supplier<EnumSet<E>> valuesSupplier;

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
        this.valuesSupplier = () -> EnumSet.allOf(type);
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
        this.valuesSupplier = () -> values.isEmpty() ? EnumSet.noneOf(type) : EnumSet.copyOf(values);
        return this;
    }

    /**
     * Sets a supplier that provides the set of selectable enum constants.
     * <p>
     * Use this to lazily or dynamically determine the values.
     *
     * @param valuesSupplier the supplier that returns the set of allowed values
     * @return the parameter itself for fluent method calls
     */
    public EnumParameter<E> withValuesProvider(Supplier<List<E>> valuesSupplier) {
        this.valuesSupplier = () -> EnumSet.copyOf(valuesSupplier.get());
        return this;
    }

    /**
     * Enumerates all values offered for this parameter.
     *
     * @return the list of selectable enum constants (declaration order of the enum type)
     */
    public List<E> getValues() {
        return List.copyOf(valuesSupplier.get());
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

        return input.getEnum(type)
                    .filter(enumValue -> valuesSupplier.get().contains(enumValue))
                    .map(E::name)
                    .orElse(null);
    }

    @Override
    public Optional<?> computeValueUpdate(Map<String, String> parameterContext) {
        return updater.apply(parameterContext)
                      .map(value -> Json.createObject().put("value", value.name()).put("text", value.toString()));
    }

    @Override
    protected Optional<E> resolveFromString(Value input) {
        return input.getEnum(type).filter(enumValue -> valuesSupplier.get().contains(enumValue));
    }
}
