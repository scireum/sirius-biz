/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.reports;

import com.fasterxml.jackson.databind.node.ObjectNode;
import sirius.kernel.di.std.Named;

/**
 * Formats a {@link Cell cell} (its JSON representation) into a HTML string.
 */
public interface CellFormat extends Named {

    /**
     * Formats the given cell.
     *
     * @param data the cell described as JSON
     * @return a HTML string which represents the rendered result
     */
    String format(ObjectNode data);

    /**
     * The raw value of a cell as String.
     *
     * @param data the cell described as JSON
     * @return a String containing the format-less value
     */
    String rawValue(ObjectNode data);
}
