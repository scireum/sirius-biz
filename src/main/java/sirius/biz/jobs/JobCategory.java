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

/**
 * Represents a category of jobs.
 * <p>
 * Categories are loaded from the system configuration by reading <tt>jobs.categories</tt>.
 */
public class JobCategory {

    /**
     * Contains the name of the default category which contains all import jobs.
     * <p>
     * Note that there is a {@link sirius.biz.jobs.batch.ImportBatchProcessFactory} readily available.
     */
    public static final String CATEGORY_IMPORT = "import";

    /**
     * Contains the name of the default category which contains all export jobs.
     * <p>
     * Note that there is a {@link sirius.biz.jobs.batch.ExportBatchProcessFactory} readily available.
     */
    public static final String CATEGORY_EXPORT = "export";

    /**
     * Contains the name of the default category which contains all report and analytics jobs.
     * <p>
     * Note that there is a {@link sirius.biz.jobs.batch.ReportBatchProcessFactory} readily available.
     */
    public static final String CATEGORY_REPORT = "report";

    /**
     * Contains the name of the default category which contains all checks and data quality jobs.
     * <p>
     * Note that there is a {@link sirius.biz.jobs.batch.CheckBatchProcessFactory} readily available.
     */
    public static final String CATEGORY_CHECK = "check";

    /**
     * Contains the name of the default category which contains all jobs that fit no other category.
     * <p>
     * Note that there is a {@link sirius.biz.jobs.batch.DefaultBatchProcessFactory} readily available,
     * which can be used for miscellaneous tasks.
     */
    public static final String CATEGORY_MISC = "misc";

    private final String name;
    private final String label;
    private final String icon;
    private final int priority;

    protected JobCategory(String name, String label, String icon, int priority) {
        this.name = name;
        this.label = label;
        this.icon = icon;
        this.priority = priority;
    }

    /**
     * Returns the technical name of the category.
     *
     * @return the name of the category
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the {@link NLS#smartGet(String) auto translated} label of the category.
     *
     * @return the label to show to the user
     */
    public String getLabel() {
        return NLS.smartGet(label);
    }

    /**
     * Returns the icon used to visualize the category.
     *
     * @return the icon of this category
     */
    public String getIcon() {
        if (Strings.isEmpty(icon)) {
            return "fa-cogs";
        }

        return icon;
    }

    /**
     * Returns the sort priority of this category.
     *
     * @return the sort priority
     */
    public int getPriority() {
        return priority;
    }
}
