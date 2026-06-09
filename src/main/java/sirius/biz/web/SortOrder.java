/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import sirius.kernel.commons.Strings;

import javax.annotation.Nullable;
import java.util.Arrays;

/**
 * Represents the supported directions for table sorting.
 */
public enum SortOrder {
    ASC(TableSorting.ORDER_ASC),
    DESC(TableSorting.ORDER_DESC);

    private final String parameterValue;

    SortOrder(String parameterValue) {
        this.parameterValue = parameterValue;
    }

    @Nullable
    public static SortOrder fromParameter(@Nullable String parameterValue) {
        return Arrays.stream(values())
                     .filter(order -> Strings.areEqual(order.parameterValue, parameterValue))
                     .findFirst()
                     .orElse(null);
    }
}

