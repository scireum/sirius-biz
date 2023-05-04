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
import sirius.kernel.di.std.Register;
import sirius.web.templates.ContentHelper;

import javax.annotation.Nonnull;

/**
 * Represents a formatter which generates a link.
 */
@Register(classes = {LinkCellFormat.class, CellFormat.class})
public class LinkCellFormat implements CellFormat {

    protected static final String TYPE = "link";
    protected static final String KEY_URL = "url";
    protected static final String KEY_VALUE = "value";

    @Override
    public String format(ObjectNode data) {
        return "<a href=\""
               + ContentHelper.escapeXML(data.get(KEY_URL).asText())
               + "\" classes=\"link\" target=\"_blank\">"
               + ContentHelper.escapeXML(data.get(KEY_VALUE).asText())
               + "</a>";
    }

    @Override
    public String rawValue(ObjectNode data) {
        StringBuilder linkUrl = new StringBuilder(data.get(KEY_URL).asText());
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
