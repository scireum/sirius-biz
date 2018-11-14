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

@Register(classes = {SparklineCellFormat.class, TableCellFormat.class})
public class SparklineCellFormat implements TableCellFormat {

    public static final String TYPE = "sparkline";
    public static final String KEY_VALUES = "values";
    public static final String KEY_VALUE = "value";

    @Override
    public String format(JSONObject data) {
        String value = data.getString(KEY_VALUE);
        String values = data.getString(KEY_VALUES);

        StringBuilder sb = new StringBuilder("<div class=\"align-right\">");
        sb.append(value);
        sb.append(" ");
        sb.append("<canvas width=\"40\" height=\"20\" class=\"sparkline\" data-sparkline=\"");
        sb.append(values);
        sb.append("\"></canvas>");
        sb.append("</div>");
        return sb.toString();
    }

    @Nonnull
    @Override
    public String getName() {
        return TYPE;
    }
}
