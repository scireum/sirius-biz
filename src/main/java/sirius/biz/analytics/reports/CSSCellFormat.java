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

@Register(classes = {CSSCellFormat.class, TableCellFormat.class})
public class CSSCellFormat implements TableCellFormat {

    public static final String TYPE = "css";
    public static final String KEY_CLASSES = "classes";
    public static final String KEY_VALUE = "value";

    @Override
    public String format(JSONObject data) {
        return "<div class=\"" + ContentHelper.escapeXML(data.getString(KEY_CLASSES)) + "\">" + ContentHelper.escapeXML(
                data.getString(KEY_VALUE)) + "</div>";
    }

    @Nonnull
    @Override
    public String getName() {
        return TYPE;
    }
}
