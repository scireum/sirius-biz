/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists;

import sirius.biz.web.BizController;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.controller.AutocompleteHelper;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.LoginRequired;

/**
 * Endpoint for autocomplete suggestions for {@link LookupValue} and {@link LookupValues} fields
 */
@Register
public class LookupValueSuggestionController extends BizController {

    @Part
    private LookupTables lookupTables;

    /**
     * Responds with possible suggestions from the given {@link LookupTable} using {@link AutocompleteHelper}.
     *
     * @param webContext the web requests calling the autocomplete service
     * @param tableName  the name of the table for which suggestions should be gathered
     * @param display    the requested {@link sirius.biz.codelists.LookupValue.Display display mode} for the label
     */
    @LoginRequired
    @Routed("/autocomplete/lookuptable/:1/:2")
    public void suggestFromLookupTable(WebContext webContext, String tableName, String display) {
        LookupValue.Display displayMode =
                Value.of(display).getEnum(LookupValue.Display.class).orElse(LookupValue.Display.NAME);
        AutocompleteHelper.handle(webContext,
                                  (query, result) -> lookupTables.fetchTable(tableName)
                                                                 .suggest(query)
                                                                 .forEach(entry -> result.accept(makeSuggestion(entry,
                                                                                                                displayMode))));
    }

    private AutocompleteHelper.Completion makeSuggestion(LookupTableEntry entry, LookupValue.Display displayMode) {
        return AutocompleteHelper.suggest(entry.getCode())
                                 .withFieldLabel(displayMode.makeDisplayString(entry))
                                 .withCompletionLabel(displayMode.makeDisplayString(entry))
                                 .withCompletionDescription(entry.getDescription());
    }
}
