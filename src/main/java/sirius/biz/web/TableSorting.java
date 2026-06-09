/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Register;
import sirius.web.http.WebContext;

/**
 * Provides common request parameters and the currently selected values for table sorting.
 */
@Register(classes = TableSorting.class)
public class TableSorting {

    public static final String PARAM_SORT = "sort";
    public static final String PARAM_ORDER = "order";

    public static final String ORDER_ASC = "asc";
    public static final String ORDER_DESC = "desc";

    /**
     * Returns the currently requested sort key or an empty string if none is active.
     *
     * @return the currently requested sort key
     */
    public String fetchCurrentSortKey() {
        return fetchCurrentSortKey(WebContext.getCurrent());
    }

    /**
     * Returns the currently requested sort key or an empty string if none is active.
     *
     * @param webContext the request to inspect
     * @return the currently requested sort key
     */
    public String fetchCurrentSortKey(WebContext webContext) {
        return webContext.get(PARAM_SORT).asString();
    }

    /**
     * Returns the currently requested order or an empty string if none is valid.
     *
     * @return the currently requested order
     */
    public String fetchCurrentOrderValue() {
        return fetchCurrentOrderValue(WebContext.getCurrent());
    }

    /**
     * Returns the currently requested order or an empty string if none is valid.
     *
     * @param webContext the request to inspect
     * @return the currently requested order
     */
    public String fetchCurrentOrderValue(WebContext webContext) {
        String requestedOrder = webContext.get(PARAM_ORDER).asString();

        if (Strings.areEqual(requestedOrder, ORDER_ASC)) {
            return ORDER_ASC;
        }

        if (Strings.areEqual(requestedOrder, ORDER_DESC)) {
            return ORDER_DESC;
        }
        return "";
    }
}

