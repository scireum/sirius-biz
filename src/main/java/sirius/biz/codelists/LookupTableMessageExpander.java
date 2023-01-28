/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists;

import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.controller.MessageExpander;

import java.util.regex.Pattern;

/**
 * Expands blocks like <tt>lookupTable:units</tt> into proper links to a {@link LookupTable}.
 */
@Register
public class LookupTableMessageExpander implements MessageExpander {

    @Part
    private LookupTables lookupTables;

    private static final Pattern LOOKUP_TABLE_PATTERN = Pattern.compile("lookupTable:([a-zA-Z0-9\\-_]+)");

    @Override
    public String expand(String message) {
        return LOOKUP_TABLE_PATTERN.matcher(message).replaceAll(match -> {
            String tableName = match.group(1);
            LookupTable table = lookupTables.fetchTable(tableName);
            return Strings.apply("""
                                         <span class="d-inline-flex flex-row align-items-baseline">
                                             <i class="fa fa-bolt"></i><a class="pl-1" href="javascript:openLookupTable('%s')">%s</a>
                                         </span>
                                         """, tableName, table.getTitle());
        });
    }
}
