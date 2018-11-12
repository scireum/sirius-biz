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

import java.time.LocalDate;
import java.util.Optional;
import java.util.function.Supplier;

public class LocalDateParameter extends Parameter<LocalDate, LocalDateParameter> {

    private final Supplier<LocalDate> defaultValue;

    public LocalDateParameter(String name, String title, Supplier<LocalDate> defaultValue) {
        super(name, title);
        this.defaultValue = defaultValue;
    }

    @Override
    public String getTemplateName() {
        return "/templates/params/datefield.html.pasta";
    }

    @Override
    protected String checkAndTransformValue(Value input) {
        if (input.isEmptyString()) {
            return NLS.toMachineString(defaultValue != null ? defaultValue.get() : null);
        }

        return NLS.toMachineString(NLS.parseUserString(LocalDate.class, input.asString()));
    }

    @Override
    protected Optional<LocalDate> resolveFromString(Value input) {
        return Optional.ofNullable(NLS.parseMachineString(LocalDate.class, input.getString()));
    }
}
