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
@Register(classes = {CSSCellFormat.class, CellFormat.class})
public class CSSCellFormat implements CellFormat {

    protected static final String TYPE = "css";
    protected static final String KEY_CLASSES = "classes";
    protected static final String KEY_VALUE = "value";

    @Override
    public String format(JSONObject data) {
        return "<div class=\"" + ContentHelper.escapeXML(data.getString(KEY_CLASSES)) + "\">" + ContentHelper.escapeXML(
                data.getString(KEY_VALUE)) + "</div>";
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
