/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.reports;

import com.fasterxml.jackson.databind.node.ObjectNode;
import sirius.biz.web.BizController;
import sirius.kernel.commons.StringCleanup;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;

/**
 * Represents a formatter which generates a link.
 */
@Register(classes = {LinkCellFormat.class, CellFormat.class})
public class LinkCellFormat implements CellFormat {

    protected static final String TYPE = "link";
    protected static final String KEY_URL = "url";
    protected static final String KEY_VALUE = "value";
    protected static final String KEY_TARGET_BLANK = "targetBlank";

    @Override
    public String format(ObjectNode data) {
        boolean newTab = data.get(KEY_TARGET_BLANK).asBoolean();
        String html = "<a href=\""
                      + Strings.cleanup(data.path(KEY_URL).asText(), StringCleanup::escapeXml)
                      + "\" classes=\"link\" ";
        if (newTab) {
            html += "target=\"_blank\" ";
        }
        html += ">" + Strings.cleanup(data.path(KEY_VALUE).asText(), StringCleanup::escapeXml) + "</a>";
        return html;
    }

    @Override
    public String rawValue(ObjectNode data) {
        StringBuilder linkUrl = new StringBuilder(data.path(KEY_URL).asText());
        if (!linkUrl.toString().startsWith("http") && linkUrl.toString().startsWith("/")) {
            linkUrl.insert(0, BizController.getBaseUrl());
        }
        return linkUrl.toString();
    }

    @Nonnull
    @Override
    public String getName() {
        return TYPE;
    }
}
