/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho;

import com.alibaba.fastjson.JSONObject;

/**
 * Represents a secondary action which can be attached to a data object.
 * <p>
 * This is used for both, {@link sirius.biz.tycho.search.OpenSearchResult} as well as for TODO insights.
 */
public class QuickAction {

    private String label;
    private String url;
    private String icon;

    /**
     * Specifies which icon should be shown for the action.
     *
     * @param icon the icon to show. Note that this must be a valid CSS class to be used in {@code <i class="..."></i>}.
     * @return the action itself for fluent method calls
     */
    public QuickAction withIcon(String icon) {
        this.icon = icon;
        return this;
    }

    /**
     * Specifies the URL to invoke if the action is clicked.
     * <p>
     * Use <tt>javascript:someFunction()</tt> to invoke a JS callback rather than navigating somewhere.
     *
     * @param url the action to open / execute
     * @return the action itself for fluent method calls
     */
    public QuickAction withUrl(String url) {
        this.url = url;
        return this;
    }

    /**
     * Specifies the label / tooltip to show for the action.
     *
     * @param label the label or tooltip of the action.
     * @return the action itself for fluent method calls
     */
    public QuickAction withLabel(String label) {
        this.label = label;
        return this;
    }

    public String getLabel() {
        return label;
    }

    public String getIcon() {
        return icon;
    }

    public String getUrl() {
        return url;
    }

    /**
     * Provides a JSON representation of this action.
     *
     * @return a JSON representation of this action
     */
    public JSONObject toJson() {
        JSONObject result = new JSONObject();
        result.put("label", label);
        result.put("icon", icon);
        result.put("url", url);

        return result;
    }
}
