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

import javax.annotation.Nonnull;

/**
 * Represents a formatter which renders a value along with a sparkline behind it.
 */
@Register(classes = {SparklineCellFormat.class, CellFormat.class})
public class SparklineCellFormat implements CellFormat {

    protected static final String TYPE = "sparkline";
    protected static final String KEY_VALUES = "values";
    protected static final String KEY_VALUE = "value";

    @Override
    public String format(ObjectNode data) {
        String value = data.path(KEY_VALUE).asText();
        String values = data.path(KEY_VALUES).asText();

        StringBuilder sb = new StringBuilder("<div class=\"text-end\">");
        sb.append(value);
        sb.append(" ");
        sb.append("<canvas width=\"40\" height=\"20\" class=\"sparkline-js\" data-sparkline=\"");
        sb.append(values);
        sb.append("\"></canvas>");
        sb.append("</div>");
        return sb.toString();
    }

    @Override
    public String rawValue(ObjectNode data) {
        return data.path(KEY_VALUE).asText(null);
    }

    @Nonnull
    @Override
    public String getName() {
        return TYPE;
    }
}
