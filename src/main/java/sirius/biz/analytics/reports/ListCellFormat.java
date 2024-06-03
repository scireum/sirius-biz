/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.reports;

import com.fasterxml.jackson.databind.node.ObjectNode;
import sirius.kernel.commons.Json;
import sirius.kernel.commons.StringCleanup;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;

/**
 * Represents a formatter which applies a list of CSS classes to the generated div container.
 */
@Register(classes = {ListCellFormat.class, CellFormat.class})
public class ListCellFormat implements CellFormat {

    protected static final String TYPE = "list";
    protected static final String KEY_VALUES = "values";

    @Override
    public String format(ObjectNode data) {
        StringBuilder sb = new StringBuilder("<ul>");
        for (String value : Json.convertToList(Json.getArray(data, KEY_VALUES), String.class)) {
            sb.append("<li>");
            sb.append(Strings.cleanup(value, StringCleanup::escapeXml));
            sb.append("</li>");
        }
        sb.append("</ul>");
        return sb.toString();
    }

    @Override
    public String rawValue(ObjectNode data) {
        return Json.tryGetArray(data, KEY_VALUES).map(Json::write).orElse(null);
    }

    @Nonnull
    @Override
    public String getName() {
        return TYPE;
    }
}
