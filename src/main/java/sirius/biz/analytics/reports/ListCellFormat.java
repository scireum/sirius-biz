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
import sirius.web.templates.ContentHelper;

import javax.annotation.Nonnull;

/**
 * Represents a formatter which applies a list of CSS classes to the generated div container.
 */
@Register(classes = {ListCellFormat.class, CellFormat.class})
public class ListCellFormat implements CellFormat {

    protected static final String TYPE = "list";
    protected static final String KEY_VALUES = "values";

    @Override
    public String format(JSONObject data) {
        StringBuilder sb = new StringBuilder("<ul>");
        for (String value : data.getJSONArray(KEY_VALUES).toJavaList(String.class)) {
            sb.append("<li>");
            sb.append(ContentHelper.escapeXML(value));
            sb.append("</li>");
        }
        sb.append("</ul>");
        return sb.toString();
    }

    @Override
    public String rawValue(JSONObject data) {
        return data.getJSONArray((KEY_VALUES)).toString();
    }

    @Nonnull
    @Override
    public String getName() {
        return TYPE;
    }
}
