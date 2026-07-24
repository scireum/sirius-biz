/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.reports;

import sirius.kernel.commons.StringCleanup;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Register;
import tools.jackson.databind.node.ObjectNode;

import javax.annotation.Nonnull;

/**
 * Renders a value along with a trend indicator next to it.
 */
@Register(classes = {TrendCellFormat.class, CellFormat.class})
public class TrendCellFormat implements CellFormat {

    protected static final String TYPE = "trend";
    protected static final String KEY_CLASSES = "classes";
    protected static final String KEY_VALUE = "value";
    protected static final String KEY_TREND = "trend";
    protected static final String KEY_ICON = "icon";
    protected static final String KEY_HINT = "hint";

    @Override
    public String format(ObjectNode data) {
        String classes = data.path(KEY_CLASSES).asString("");
        String value = data.path(KEY_VALUE).asString("");
        String trend = data.path(KEY_TREND).asString("");
        String hint = data.path(KEY_HINT).asString("");
        String icon = data.path(KEY_ICON).asString("");

        StringBuilder builder = new StringBuilder("<div class=\"text-end\"");
        if (Strings.isFilled(hint)) {
            builder.append(" title=\"");
            builder.append(Strings.cleanup(hint, StringCleanup::escapeXml));
            builder.append("\"");
        }
        builder.append(">");
        if (Strings.isFilled(value)) {
            if (Strings.isFilled(trend)) {
                builder.append("<span>");
                builder.append(Strings.cleanup(value, StringCleanup::escapeXml));
                builder.append("</span> (<span class=\"");
                builder.append(Strings.cleanup(classes, StringCleanup::escapeXml));
                builder.append("\">");
                builder.append(Strings.cleanup(trend, StringCleanup::escapeXml));
                builder.append("</span>)");
            } else {
                builder.append(Strings.cleanup(value, StringCleanup::escapeXml));
            }
        } else {
            builder.append("<span class=\"");
            builder.append(Strings.cleanup(classes, StringCleanup::escapeXml));
            builder.append("\">");
            builder.append(Strings.cleanup(trend, StringCleanup::escapeXml));
            builder.append("</span>");
        }
        if (Strings.isFilled(icon)) {
            if (Strings.isEmpty(trend)) {
                builder.append("<span class=\"");
                builder.append(Strings.cleanup(classes, StringCleanup::escapeXml));
                builder.append("\">");
            }
            builder.append(" <i class=\"" + icon + "\"></i>");
            if (Strings.isEmpty(trend)) {
                builder.append("</span>");
            }
        }

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
