/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists;

import sirius.biz.tenants.TenantUserManager;
import sirius.biz.web.BizController;
import sirius.kernel.commons.Limit;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.nls.NLS;
import sirius.web.controller.AutocompleteHelper;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.UserContext;
import sirius.web.services.InternalService;
import sirius.web.services.JSONStructuredOutput;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Provides autocomplete suggestions for {@link LookupValue} and {@link LookupValues} fields as well as the services
 * required by the lookup table modal.
 */
@Register
public class LookupTableController extends BizController {

    private static final int MAX_SUGGESTIONS_ITEMS = 25;
    private static final int PAGE_SIZE = 25;

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
    @Routed("/system/lookuptable/autocomplete/:1/:2/:3")
    public void suggestFromLookupTable(WebContext webContext,
                                       String tableName,
                                       String display,
                                       String extendedDisplay) {
        boolean considerDeprecatedValues = webContext.get("considerDeprecatedValues").asBoolean();

        LookupTable lookupTable = lookupTables.fetchTable(tableName);
        LookupValue.Display fieldDisplayMode =
                Value.of(display).getEnum(LookupValue.Display.class).orElse(LookupValue.Display.NAME);
        LookupValue.Display completionDisplayMode =
                Value.of(extendedDisplay).getEnum(LookupValue.Display.class).orElse(LookupValue.Display.NAME);
        AutocompleteHelper.handle(webContext, (query, result) -> {
            lookupTable.suggest(query, NLS.getCurrentLanguage(), considerDeprecatedValues)
                       .limit(MAX_SUGGESTIONS_ITEMS)
                       .map(entry -> entry.toAutocompletion(fieldDisplayMode, completionDisplayMode))
                       .forEach(result);
        });
    }

    /**
     * Provides some metadata for a given lookup table along with the selected list of entries in it.
     *
     * @param webContext the request to respond to
     * @param output     the JSON output to generate
     * @param tableName  the name of the lookup table being queried
     */
    @Routed("/system/lookuptable/info/:1")
    @InternalService
    public void lookupTableInfo(WebContext webContext, JSONStructuredOutput output, String tableName) {
        LookupTable lookupTable = lookupTables.fetchTable(tableName);
        output.property("title", lookupTable.getTitle());
        output.property("description", lookupTable.getDescription());
        int totalEntriesInTable = lookupTable.count();
        output.property("count", totalEntriesInTable);

        int itemsToSkip = webContext.get("skip").asInt(0);
        List<LookupTableEntry> entries =
                lookupTable.search(webContext.get("query").asString(), new Limit(itemsToSkip, PAGE_SIZE + 1))
                           .collect(Collectors.toList());
        boolean hasMore = entries.size() > PAGE_SIZE;
        if (hasMore) {
            entries.removeLast();
        }

        if (itemsToSkip - PAGE_SIZE >= 0) {
            output.property("prevSkip", itemsToSkip - PAGE_SIZE);
        }
        if (hasMore) {
            output.property("nextSkip", itemsToSkip + entries.size());
        }
        output.property("paginationInfo",
                        NLS.fmtr("LookupTableController.paginationInfo")
                           .set("from", itemsToSkip + 1)
                           .set("to", itemsToSkip + entries.size())
                           .format());
        output.property("searchPlaceholder",
                        NLS.fmtr("LookupTableController.searchPlaceholder").set("count", totalEntriesInTable).format());

        boolean showSource =
                UserContext.getCurrentUser().hasPermission(TenantUserManager.PERMISSION_SYSTEM_TENANT_MEMBER);
        LookupValue.Display labelDisplay =
                webContext.get("labelFormat").getEnum(LookupValue.Display.class).orElse(LookupValue.Display.NAME);

        output.property("more", hasMore);
        output.beginArray("entries");
        for (LookupTableEntry entry : entries) {
            output.beginObject("entry");
            output.property("code", entry.getCode());
            output.property("showCode", true);
            output.property("label", labelDisplay.makeDisplayString(entry));
            output.property("name", entry.getName());
            output.property("description", entry.getDescription());
            output.property("deprecated", entry.isDeprecated());
            if (showSource) {
                output.property("source", entry.getSource());
            }

            output.endObject();
        }
        output.endArray();
    }
}
