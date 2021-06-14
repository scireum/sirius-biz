/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists;

import sirius.biz.web.BizController;
import sirius.kernel.commons.Limit;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.nls.NLS;
import sirius.web.controller.AutocompleteHelper;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.LoginRequired;
import sirius.web.services.JSONStructuredOutput;

@Register
public class LookupTableController extends BizController {

    @Part
    private LookupTables lookupTables;

    @Routed(value = "/system/lookup-tables", jsonCall = true)
    @LoginRequired
    public void suggestLookupValues(WebContext webContext, JSONStructuredOutput output, String lookupTable) {
        AutocompleteHelper.handle(webContext, (query, consumer) -> {
            lookupTables.fetchTable(lookupTable)
                        .performSuggest(new Limit(0, 15), query, NLS.getCurrentLang())
                        .forEach(entry -> {
                            consumer.accept(entry.toAutocompletion());
                        });
        });
    }
}
