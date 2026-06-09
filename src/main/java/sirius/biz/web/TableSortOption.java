/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import sirius.db.mixing.Mapping;

import java.util.Objects;

/**
 * Describes a whitelisted field which can be used for table sorting and displayed as an option in a sorting control.
 */
public class TableSortOption {

    private final String key;
    private final String labelKey;
    private final Mapping mapping;

    /**
     * Creates a new sortable table option.
     *
     * @param key      the technical key used in request parameters
     * @param labelKey the translation key used to display the option
     * @param mapping  the mapping to sort by
     */
    public TableSortOption(String key, String labelKey, Mapping mapping) {
        this.key = Objects.requireNonNull(key);
        this.labelKey = Objects.requireNonNull(labelKey);
        this.mapping = Objects.requireNonNull(mapping);
    }

    public String getKey() {
        return key;
    }

    public String getLabelKey() {
        return labelKey;
    }

    public Mapping getMapping() {
        return mapping;
    }
}

