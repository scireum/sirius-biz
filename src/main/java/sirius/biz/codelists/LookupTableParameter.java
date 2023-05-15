/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists;

import sirius.biz.jobs.params.ParameterBuilder;
import sirius.kernel.commons.Json;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;

import java.util.Map;
import java.util.Optional;

/**
 * Provides a parameter which lets the user select a value from the given {@link LookupTable}.
 */
public class LookupTableParameter extends ParameterBuilder<String, LookupTableParameter> {

    @Part
    private static LookupTables lookupTables;

    private boolean customValuesAccepted = false;

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

    /**
     * Specifies that custom values are accepted as well.
     *
     * @return the parameter itself for fluent method calls
     */
    public LookupTableParameter withCustomValues() {
        this.customValuesAccepted = true;
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
        Optional<String> result = lookupTables.fetchTable(lookupTable).normalizeInput(input.getString());

        if (customValuesAccepted) {
            return result.orElse(input.getString());
        } else {
            return result.orElseThrow(() -> Exceptions.createHandled()
                                                      .withNLSKey("LookupValue.invalidValue")
                                                      .set("value", input.asString())
                                                      .handle());
        }
    }

    @Override
    public Optional<?> computeValueUpdate(Map<String, String> parameterContext) {
        return updater.apply(parameterContext)
                      .map(this::createLookupValue)
                      .map(value -> Json.createObject()
                                        .put("value", value.getValue())
                                        .put("text", value.resolveDisplayString()));
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
                                                  customValuesAccepted ?
                                                  LookupValue.CustomValues.ACCEPT :
                                                  LookupValue.CustomValues.REJECT,
                                                  display,
                                                  display,
                                                  LookupValue.Export.CODE);
        lookupValue.setValue(currentValue);

        return lookupValue;
    }
}
