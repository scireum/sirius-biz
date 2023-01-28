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
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Provides a parameter which accepts local dates.
 */
public class LocalDateParameter extends ParameterBuilder<LocalDate, LocalDateParameter> {

    private Supplier<LocalDate> defaultValueSupplier;

    /**
     * Creates a new parameter with the given name and label.
     *
     * @param name  the name of the parameter
     * @param label the label of the parameter, which will be {@link NLS#smartGet(String) auto translated}
     */
    public LocalDateParameter(String name, String label) {
        super(name, label);
    }

    /**
     * Specifies the default value to use.
     * <p>
     * A <tt>Supplier</tt> is used instead of a constant value as most probably this parameter is
     * only declared once but has to be able to provide an "up-to-date" value like "today".
     *
     * @param defaultValueSupplier a supplier which returns a default value to use.
     * @return the parameter itself for fluent method calls
     */
    public LocalDateParameter withDefault(Supplier<LocalDate> defaultValueSupplier) {
        this.defaultValueSupplier = defaultValueSupplier;
        return this;
    }

    @Override
    public String getTemplateName() {
        return "/templates/biz/jobs/params/datefield.html.pasta";
    }

    @Override
    protected String checkAndTransformValue(Value input) {
        if (input.isEmptyString()) {
            return NLS.toMachineString(defaultValueSupplier != null ? defaultValueSupplier.get() : null);
        }

        return NLS.toMachineString(NLS.parseUserString(LocalDate.class, input.asString()));
    }

    @Override
    public Optional<?> computeValueUpdate(Map<String, String> parameterContext) {
        return super.computeValueUpdate(parameterContext).map(NLS::toUserString);
    }

    @Override
    protected Optional<LocalDate> resolveFromString(Value input) {
        return Optional.ofNullable(NLS.parseMachineString(LocalDate.class, input.getString()));
    }
}
