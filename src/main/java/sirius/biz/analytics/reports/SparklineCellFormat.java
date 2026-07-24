/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.reports;

import sirius.kernel.di.std.Register;
import tools.jackson.databind.node.ObjectNode;

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
        String value = data.path(KEY_VALUE).asString("");
        String values = data.path(KEY_VALUES).asString("");

        StringBuilder builder = new StringBuilder("<div class=\"text-end\">");
        builder.append(value);
        builder.append(" ");
        builder.append("<canvas width=\"40\" height=\"20\" class=\"sparkline-js\" data-sparkline=\"");
        builder.append(values);
        builder.append("\"></canvas>");
        builder.append("</div>");
        return builder.toString();
    }

    @Override
    public String rawValue(ObjectNode data) {
        return data.path(KEY_VALUE).asString(null);
    }

    @Nonnull
    @Override
    public String getName() {
        return TYPE;
    }
}
