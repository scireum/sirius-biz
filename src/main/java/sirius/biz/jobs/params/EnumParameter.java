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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Provides the selection of an enum constant as parameter.
 *
 * @param <E> the enum type to select from
 */
public class EnumParameter<E extends Enum<E>> extends Parameter<E, EnumParameter<E>> {

    private final Class<E> type;
    private E defaultValue;

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
     * Enumerates all values provided by the enum.
     *
     * @return the list of value defined by the enum type
     */
    public List<E> getValues() {
        return Arrays.asList(type.getEnumConstants());
    }

    @Override
    public String getTemplateName() {
        return "/templates/biz/jobs/params/enum.html.pasta";
    }

    @Override
    protected String checkAndTransformValue(Value input) {
        return input.getEnum(type).map(E::name).orElse(defaultValue != null ? defaultValue.name() : null);
    }

    @Override
    protected Optional<E> resolveFromString(Value input) {
        return input.getEnum(type);
    }
}
