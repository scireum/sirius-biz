/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import sirius.db.mixing.Mapping;
import sirius.kernel.commons.Strings;
import sirius.kernel.nls.NLS;

import java.util.Objects;

/**
 * Describes a whitelisted field which can be used for table sorting and displayed as an option in a sorting control.
 *
 * @param label   the label used to display the option
 * @param mapping the mapping to sort by
 */
public record TableSortOption(String label, Mapping mapping) {

    /**
     * Creates a new sortable table option.
     */
    public TableSortOption {
        if (Strings.isEmpty(label)) {
            throw new IllegalArgumentException("label");
        }

        Objects.requireNonNull(mapping, "mapping");
    }

    public String getLabel() {
        return NLS.smartGet(label);
    }

    public Mapping getMapping() {
        return mapping;
    }
}

