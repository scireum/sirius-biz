/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists;

import sirius.biz.web.BizController;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.controller.AutocompleteHelper;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.LoginRequired;

@Register
public class LookupValueSuggestionController extends BizController {

    @Part
    private LookupTables lookupTables;

    @LoginRequired
    @Routed("/autocomplete/lookuptable/:1")
    public void suggestFromLookupTable(WebContext webContext, String tableName) {
        //TODO use new AutocompleteHelper syntax + respect display settings of field
        AutocompleteHelper.handle(webContext,
                                  (query, result) -> lookupTables.fetchTable(tableName)
                                                                 .suggest(query)
                                                                 .forEach(entry -> result.accept(new AutocompleteHelper.Completion(
                                                                         entry.getCode(),
                                                                         entry.getName(),
                                                                         entry.getDescription()))));
    }
}
