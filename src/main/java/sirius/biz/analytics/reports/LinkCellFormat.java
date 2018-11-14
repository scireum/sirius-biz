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

@Register(classes = {LinkCellFormat.class, TableCellFormat.class})
public class LinkCellFormat implements TableCellFormat {

    public static final String TYPE = "link";
    public static final String KEY_URL = "url";
    public static final String KEY_VALUE = "value";

    @Override
    public String format(JSONObject data) {
        return "<a href=\""
               + ContentHelper.escapeXML(data.getString(KEY_URL))
               + "\" classes=\"link\" target=\"_blank\">"
               + ContentHelper.escapeXML(data.getString(KEY_VALUE))
               + "</a>";
    }

    @Nonnull
    @Override
    public String getName() {
        return TYPE;
    }
}
