/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.charts;

import java.util.List;

public class Charts {
    public static String formatLabels(List<String> labels) {
        StringBuilder sb = new StringBuilder();
        for (String label : labels) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append("'");
            sb.append(label);
            sb.append("'");
        }

        return sb.toString();
    }
}
