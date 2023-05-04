/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.reports;

import com.fasterxml.jackson.databind.node.ObjectNode;
import sirius.kernel.di.std.Register;
import sirius.web.templates.ContentHelper;

import javax.annotation.Nonnull;

/**
 * Represents a formatter which applies a list of CSS classes to the generated div container.
 */
@Register(classes = {CSSCellFormat.class, CellFormat.class})
public class CSSCellFormat implements CellFormat {

    protected static final String TYPE = "css";
    protected static final String KEY_CLASSES = "classes";
    protected static final String KEY_VALUE = "value";

    @Override
    public String format(ObjectNode data) {
        return "<div class=\""
               + ContentHelper.escapeXML(data.get(KEY_CLASSES).asText())
               + "\">"
               + ContentHelper.escapeXML(data.get(KEY_VALUE).asText())
               + "</div>";
    }

    @Override
    public String rawValue(ObjectNode data) {
        return data.get(KEY_VALUE).asText();
    }

    @Nonnull
    @Override
    public String getName() {
        return TYPE;
    }
}
