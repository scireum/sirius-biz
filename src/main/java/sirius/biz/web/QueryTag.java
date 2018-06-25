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
 * Can be embedded in a query parsed by the {@link QueryCompiler}.
 * <p>
 * Such a compiler is created and applied by a {@link SQLPageHelper} where {@link SQLPageHelper#enableAdvancedSearch()} is
 * invoked.
 * <p>
 * A query tag represents a special kind of filter which is suggested by a {@link QueryTagSuggester} and then
 * transformed by a {@link QueryTagHandler}.
 * <p>
 * The <tt>taggedSearch</tt> component can be used to render such queries properly and also contains all the logic
 * required to invoke the autocompletion via the {@link QueryTagController}.
 */
public class QueryTag {

    private String type;
    private String value;
    private String label;
    private String color;

    private static final Pattern PATTERN = Pattern.compile("\\|\\|([^|]+)\\|([^|]+)\\|([^|]+)\\|.*");

    /**
     * Creates a new tag.
     *
     * @param type  the type of the tag, used to identify the {@link QueryTagHandler} to use
     * @param color the color used to render the tag in the search box
     * @param value the value passed to the {@link QueryTagHandler}
     * @param label the value shown to the user
     */
    public QueryTag(String type, String color, String value, String label) {
        this.type = type;
        this.color = color;
        this.value = value;
        this.label = label;

        if (type != null && type.contains("|")) {
            throw new IllegalArgumentException("type contains |");
        }
        if (value != null && value.contains("|")) {
            throw new IllegalArgumentException("value contains |");
        }
        if (label != null && label.contains("|")) {
            throw new IllegalArgumentException("label contains |");
        }
    }

    /**
     * Parses the tag from a given search query.
     *
     * @param tag the tag to parse, see {@link #toString()}
     * @return the parsed tag
     */
    public static QueryTag parse(String tag) {
        if (Strings.isFilled(tag)) {
            Matcher m = PATTERN.matcher(tag);
            if (m.matches()) {
                return new QueryTag(m.group(1), m.group(2), m.group(3), null);
            }
        }

        return new QueryTag(null, null, null, null);
    }

    /**
     * The type of the tag, used to identify the {@link QueryTagHandler} to use.
     *
     * @return the type of the tag
     */
    public String getType() {
        return type;
    }

    /**
     * Returns the filter value passed to the {@link QueryTagHandler}.
     *
     * @return the effective filter value
     */
    public String getValue() {
        return value;
    }

    /**
     * Returns the label shown to the user.
     *
     * @return the label used to represent this tag
     */
    public String getLabel() {
        return label;
    }

    /**
     * Returns the color or CSS class used to render this tag.
     *
     * @return the color of this tag
     */
    public String getColor() {
        return color;
    }

    @Override
    public String toString() {
        return "||" + type + "|" + color + "|" + value + "|" + label + "||";
    }
}
