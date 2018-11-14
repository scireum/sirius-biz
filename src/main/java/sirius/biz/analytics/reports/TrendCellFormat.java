/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.reports;

import com.alibaba.fastjson.JSONObject;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Register;
import sirius.web.templates.ContentHelper;

import javax.annotation.Nonnull;

@Register(classes = {TrendCellFormat.class, TableCellFormat.class})
public class TrendCellFormat implements TableCellFormat {

    public static final String TYPE = "trend";
    public static final String KEY_CLASSES = "classes";
    public static final String KEY_VALUE = "value";
    public static final String KEY_TREND = "trend";
    public static final String KEY_ICON = "icon";
    public static final String KEY_HINT = "hint";

    @Override
    public String format(JSONObject data) {
        String classes = data.getString(KEY_CLASSES);
        String value = data.getString(KEY_VALUE);
        String trend = data.getString(KEY_TREND);
        String hint = data.getString(KEY_HINT);
        String icon = data.getString(KEY_ICON);

        StringBuilder sb = new StringBuilder("<div class=\"align-right\"");
        if (Strings.isFilled(hint)) {
            sb.append(" title=\"");
            sb.append( ContentHelper.escapeXML(hint));
            sb.append("\"");
        }
        sb.append(">");
        if (Strings.isFilled(value)) {
            if (Strings.isFilled(trend)) {
                sb.append("<span>");
                sb.append(ContentHelper.escapeXML(value));
                sb.append("</span> (<span class=\"");
                sb.append(ContentHelper.escapeXML(classes));
                sb.append("\">");
                sb.append(ContentHelper.escapeXML(trend));
                sb.append("</span>)");
            } else {
                sb.append(ContentHelper.escapeXML(value));
            }
        } else {
            sb.append("<span class=\"");
            sb.append(ContentHelper.escapeXML(classes));
            sb.append("\">");
            sb.append(ContentHelper.escapeXML(trend));
            sb.append("</span>");

        }
        if (Strings.isFilled(icon)) {
            if (Strings.isEmpty(trend)) {
                sb.append("<span class=\"");
                sb.append(ContentHelper.escapeXML(classes));
                sb.append("\">");
            }
            sb.append(" <i class=\"fa " + icon + "\"></i>");
            if (Strings.isEmpty(trend)) {
                sb.append("</span>");
            }
        }

        sb.append("</div>");
        return sb.toString();
    }

    @Nonnull
    @Override
    public String getName() {
        return TYPE;
    }
}
