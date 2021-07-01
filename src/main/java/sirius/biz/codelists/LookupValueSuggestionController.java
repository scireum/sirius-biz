/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists;

import sirius.biz.web.BizController;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.controller.AutocompleteHelper;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.LoginRequired;

import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Provides autocomplete suggestions for {@link LookupValue} and {@link LookupValues} fields
 */
@Register
public class LookupValueSuggestionController extends BizController {

    public static final int MAX_SUGGESTIONS_ITEMS = 25;

    @Part
    private LookupTables lookupTables;

    /**
     * Responds with possible suggestions from the given {@link LookupTable} using {@link AutocompleteHelper}.
     *
     * @param webContext      the web requests calling the autocomplete service
     * @param tableName       the name of the table for which suggestions should be gathered
     * @param display         the requested {@link sirius.biz.codelists.LookupValue.Display display mode} for the field label
     * @param extendedDisplay the requested {@link sirius.biz.codelists.LookupValue.Display display mode} for the completion label
     */
    @LoginRequired
    @Routed("/autocomplete/lookuptable/:1/:2/:3")
    public void suggestFromLookupTable(WebContext webContext,
                                       String tableName,
                                       String display,
                                       String extendedDisplay) {
        LookupValue.Display fieldDisplayMode =
                Value.of(display).getEnum(LookupValue.Display.class).orElse(LookupValue.Display.NAME);
        LookupValue.Display completionDisplayMode =
                Value.of(extendedDisplay).getEnum(LookupValue.Display.class).orElse(LookupValue.Display.NAME);
        AutocompleteHelper.handle(webContext,
                                  (query, result) -> performSuggest(tableName,
                                                                    fieldDisplayMode,
                                                                    completionDisplayMode,
                                                                    query,
                                                                    result));
    }

    private void performSuggest(String tableName,
                                LookupValue.Display fieldDisplayMode,
                                LookupValue.Display completionDisplayMode,
                                String query,
                                Consumer<AutocompleteHelper.Completion> result) {
        Stream<LookupTableEntry> entryStream;
        if (Strings.isEmpty(query)) {
            entryStream = lookupTables.fetchTable(tableName).scan();
        } else {
            entryStream = lookupTables.fetchTable(tableName).suggest(query);
        }

        entryStream.limit(MAX_SUGGESTIONS_ITEMS)
                   .forEach(entry -> result.accept(entry.toAutocompletion(fieldDisplayMode, completionDisplayMode)));
    }
}
