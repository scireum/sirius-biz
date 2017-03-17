/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import sirius.kernel.commons.Strings;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by aha on 27.01.17.
 */
public class QueryTag {
    private String type;
    private String value;
    private String label;
    private String color;

    public QueryTag(String type, String color, String value, String label) {
        this.type = type;
        this.color = color;
        this.value = value;
        this.label = label;
    }

    public static final Pattern PATTERN = Pattern.compile("::([^:]+):([^:]+):([^:]+):.*");

    public static QueryTag parse(String tag) {
        if (Strings.isFilled(tag)) {
            Matcher m = PATTERN.matcher(tag);
            if (m.matches()) {
                return new QueryTag(m.group(1), m.group(2), m.group(3), null);
            }
        }

        return new QueryTag(null, null, null, null);
    }

    public String getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }

    public String getColor() {
        return color;
    }

    public String toString() {
        return "::" + type + ":" + color + ":" + value + ":" + label + "::";
    }
}
