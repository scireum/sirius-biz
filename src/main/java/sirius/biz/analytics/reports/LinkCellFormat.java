/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.reports;

import com.alibaba.fastjson.JSONObject;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Register;
import sirius.web.templates.ContentHelper;

import javax.annotation.Nonnull;

/**
 * Represents a formatter which generates a link.
 */
@Register(classes = {LinkCellFormat.class, CellFormat.class})
public class LinkCellFormat implements CellFormat {

    @ConfigValue("product.baseUrl")
    private static String productBaseUrl;

    protected static final String TYPE = "link";
    protected static final String KEY_URL = "url";
    protected static final String KEY_VALUE = "value";

    @Override
    public String format(JSONObject data) {
        return "<a href=\""
               + ContentHelper.escapeXML(data.getString(KEY_URL))
               + "\" classes=\"link\" target=\"_blank\">"
               + ContentHelper.escapeXML(data.getString(KEY_VALUE))
               + "</a>";
    }

    @Override
    public String rawValue(JSONObject data) {
        StringBuilder linkUrl = new StringBuilder(data.getString(KEY_URL));
        if (!linkUrl.toString().startsWith("http") && linkUrl.toString().startsWith("/")) {
            linkUrl.insert(0, productBaseUrl);
        }
        return linkUrl.toString();
    }

    @Nonnull
    @Override
    public String getName() {
        return TYPE;
    }
}
