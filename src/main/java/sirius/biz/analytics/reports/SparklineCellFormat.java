/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.reports;

import com.alibaba.fastjson.JSONObject;
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
    public String format(JSONObject data) {
        String value = data.getString(KEY_VALUE);
        String values = data.getString(KEY_VALUES);

        StringBuilder sb = new StringBuilder("<div class=\"text-right\">");
        sb.append(value);
        sb.append(" ");
        sb.append("<canvas width=\"40\" height=\"20\" class=\"sparkline-js\" data-sparkline=\"");
        sb.append(values);
        sb.append("\"></canvas>");
        sb.append("</div>");
        return sb.toString();
    }

    @Override
    public String rawValue(JSONObject data) {
        return data.getString(KEY_VALUE);
    }

    @Nonnull
    @Override
    public String getName() {
        return TYPE;
    }
}
