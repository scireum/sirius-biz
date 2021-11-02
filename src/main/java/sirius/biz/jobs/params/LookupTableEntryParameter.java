/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.params;

import sirius.biz.codelists.LookupTableEntry;
import sirius.biz.codelists.LookupTables;
import sirius.kernel.commons.Limit;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;
import sirius.kernel.nls.NLS;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Permits to select a {@link LookupTableEntry} as parameter.
 */
public class LookupTableEntryParameter extends ParameterBuilder<String, LookupTableEntryParameter> {

    @Part
    private static LookupTables lookupTables;

    private final String lookupTable;
    private String defaultValue;

    /**
     * Creates a new parameter with the given name and label.
     *
     * @param name        the name of the parameter
     * @param label       the label of the parameter, which will be {@link NLS#smartGet(String) auto translated}
     * @param lookupTable the name of the lookup table to retrieve values from
     */
    public LookupTableEntryParameter(String name, String label, String lookupTable) {
        super(name, label);
        this.lookupTable = lookupTable;
    }

    /**
     * Specifies the default value to use.
     *
     * @param defaultValue the default value to use
     * @return the parameter itself for fluent method calls
     */
    public LookupTableEntryParameter withDefault(String defaultValue) {
        this.defaultValue = defaultValue;
        return this;
    }

    /**
     * Enumerates all entries provided by the lookup table.
     *
     * @return the list of entries defined by the lookup table
     */
    public List<LookupTableEntry> getValues() {
        return lookupTables.fetchTable(lookupTable)
                           .scan(NLS.getCurrentLang(), Limit.UNLIMITED)
                           .collect(Collectors.toList());
    }

    /**
     * Returns the name of the template used to render the parameter in the UI.
     *
     * @return the name or path of the template used to render the parameter
     */
    @Override
    public String getTemplateName() {
        return "/templates/biz/jobs/params/lookuptableentry.html.pasta";
    }

    @Override
    protected String checkAndTransformValue(@Nonnull Value input) {
        if (!lookupTables.fetchTable(lookupTable).contains(input.asString())) {
            return defaultValue;
        }
        return input.asString();
    }

    @Override
    protected Optional<String> resolveFromString(@Nonnull Value input) {
        if (input.isEmptyString()) {
            return Optional.empty();
        }
        return lookupTables.fetchTable(lookupTable).normalize(input.asString());
    }
}
