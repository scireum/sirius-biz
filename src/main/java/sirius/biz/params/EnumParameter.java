/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.params;

import sirius.kernel.commons.Value;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class EnumParameter<E extends Enum<E>> extends Parameter<E, EnumParameter<E>> {

    private final Class<E> type;
    private E defaultValue;

    public EnumParameter(String name, String title, Class<E> type) {
        super(name, title);
        this.type = type;
    }

    public EnumParameter<E> withDefault(E defaultValue) {
        this.defaultValue = defaultValue;
        return this;
    }

    public List<E> getValues() {
        return Arrays.asList(type.getEnumConstants());
    }

    @Override
    public String getTemplateName() {
        return "/templates/params/enum.html.pasta";
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
