/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import sirius.kernel.commons.MultiMap;

import java.util.stream.Stream;

/**
 * Used by the <tt>t:actions</tt> tag to represents a selectable action.
 * <p>
 * The actions tag receives a list of actions which are grouped by categories. Each action has a label, category, url
 * and optionally an icon and description.
 */
public class Action {

    private static final String[] COLOR_CLASSES = {"text-sirius-cyan",
                                                   "text-sirius-green",
                                                   "text-sirius-violet",
                                                   "text-sirius-orange",
                                                   "text-sirius-yellow",
                                                   "text-sirius-deep-blue"};
    private final String label;
    private final String url;
    private final String category;

    private String description;
    private String icon;

    /**
     * Groups a given stream of actions by their category.
     *
     * @param actions a stream of actions
     * @return a map containing all actions grouped by their category.
     */
    public static MultiMap<String, Action> groupByCategory(Stream<Action> actions) {
        MultiMap<String, Action> result = MultiMap.createOrdered();
        actions.forEach(action -> result.put(action.getCategory(), action));
        return result;
    }

    /**
     * Determines the color to used for a given label.
     *
     * @param label the label to determine the color from
     * @return the "randomly" selected color for the given label
     */
    public static String fetchColorForLabel(String label) {
        return COLOR_CLASSES[Math.abs(label.hashCode() % COLOR_CLASSES.length)];
    }

    /**
     * Creates a new action.
     *
     * @param label    the translated label to show
     * @param url      the url to link to / to execute
     * @param category the translated category to which this action belongs
     */
    public Action(String label, String url, String category) {
        this.label = label;
        this.url = url;
        this.category = category;
    }

    /**
     * Specifies a CSS class to use as icon.
     *
     * @param icon a CSS class (e.g. one provided by Fontawesome)
     * @return the action itself for fluent method calls
     */
    public Action withIcon(String icon) {
        this.icon = icon;
        return this;
    }

    /**
     * Specifies a description for the action.
     *
     * @param description the translated description to show
     * @return the action itself for fluent method calls¡
     */
    public Action withDescription(String description) {
        this.description = description;
        return this;
    }

    public String getColorClass() {
        return fetchColorForLabel(label);
    }

    public String getLabel() {
        return label;
    }

    public String getUrl() {
        return url;
    }

    public String getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public String getIcon() {
        return icon;
    }
}
