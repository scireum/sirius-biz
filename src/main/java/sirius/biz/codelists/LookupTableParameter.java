/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists;

import sirius.biz.jobs.params.ParameterBuilder;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Provides a parameter which lets the user select a value from the given {@link LookupTable}.
 */
public class LookupTableParameter extends ParameterBuilder<String, LookupTableParameter> {

    @Part
    private static LookupTables lookupTables;

    private final String lookupTable;
    private LookupValue.Display display = LookupValue.Display.CODE_AND_NAME;

    /**
     * Creates a new parameter with the given name and label.
     *
     * @param name        the name of the parameter
     * @param label       the label which will be {@link sirius.kernel.nls.NLS#smartGet(String) auto translated}
     * @param lookupTable the underlying lookup table to use
     */
    public LookupTableParameter(String name, String label, String lookupTable) {
        super(name, label);
        this.lookupTable = lookupTable;
    }

    /**
     * Specifies the display mode to use when rendering the autocomplete field.
     *
     * @param display the display mode to use
     * @return the parameter itself for fluent method calls
     */
    public LookupTableParameter withDisplayMode(LookupValue.Display display) {
        this.display = display;
        return this;
    }

    @Override
    protected String getTemplateName() {
        return "/templates/biz/jobs/params/lookuptable.html.pasta";
    }

    @Override
    protected String checkAndTransformValue(Value input) {
        if (input.isEmptyString()) {
            return null;
        }
        return lookupTables.fetchTable(lookupTable)
                           .normalizeInput(input.getString())
                           .orElseThrow(() -> Exceptions.createHandled()
                                                        .withNLSKey("LookupTableParameter.invalidValue")
                                                        .set("value", input.asString())
                                                        .handle());
    }

    @Override
    public Optional<?> updateValue(Map<String, String> ctx) {
        return updater.apply(ctx).map(this::createLookupValue).map(value -> {
            Map<String, String> map = new HashMap<>();
            map.put("value", value.getValue());
            map.put("text", value.resolveDisplayString());
            return map;
        });
    }

    @Override
    protected Optional<String> resolveFromString(Value input) {
        return input.asOptionalString();
    }

    /**
     * Creates a wrapper {@link LookupValue} used in the UI.
     *
     * @param currentValue the currently selected value
     * @return a lookup value representing the settings of this parameter and the currently selected value
     */
    public LookupValue createLookupValue(String currentValue) {
        LookupValue lookupValue = new LookupValue(lookupTable,
                                                  LookupValue.CustomValues.REJECT,
                                                  display,
                                                  display,
                                                  LookupValue.Export.CODE);
        lookupValue.setValue(currentValue);

        return lookupValue;
    }
}
