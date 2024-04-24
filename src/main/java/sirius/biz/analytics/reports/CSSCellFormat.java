/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.reports;

import com.fasterxml.jackson.databind.node.ObjectNode;
import sirius.kernel.commons.StringCleanup;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Register;

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
               + Strings.cleanup(data.path(KEY_CLASSES).asText(), StringCleanup::escapeXml)
               + "\">"
               + Strings.cleanup(data.path(KEY_VALUE).asText(), StringCleanup::escapeXml)
               + "</div>";
    }

    @Override
    public String rawValue(ObjectNode data) {
        return data.path(KEY_VALUE).asText();
    }

    @Nonnull
    @Override
    public String getName() {
        return TYPE;
    }
}
