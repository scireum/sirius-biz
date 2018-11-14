/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs;

import sirius.kernel.commons.Strings;
import sirius.kernel.nls.NLS;

public class JobCategory {

    public static final String CATEGORY_IMPORT = "import";
    public static final String CATEGORY_EXPORT = "export";
    public static final String CATEGORY_REPORT = "report";
    public static final String CATEGORY_CHECK = "check";
    public static final String CATEGORY_MISC = "misc";

    private String name;
    private String label;
    private String icon;
    private int priority;

    public JobCategory(String name, String label, String icon, int priority) {
        this.name = name;
        this.label = label;
        this.icon = icon;
        this.priority = priority;
    }

    public String getName() {
        return name;
    }

    public String getLabel() {
        return NLS.smartGet(label);
    }

    public String getIcon() {
        if (Strings.isEmpty(icon)) {
            return "fa-cogs";
        }

        return icon;
    }

    public int getPriority() {
        return priority;
    }
}
