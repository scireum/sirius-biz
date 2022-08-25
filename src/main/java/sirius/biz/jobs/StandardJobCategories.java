/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs;

/**
 * Represents a set of default categories used by SIRIUS.
 */
public class StandardJobCategories {

    private StandardJobCategories() {
    }

    /**
     * Used for miscellaneous jobs with not better matching category.
     */
    public static final String MISC = "$StandardJobCategories.misc";

    /**
     * Used for system administration jobs.
     */
    public static final String SYSTEM_ADMINISTRATION = "$StandardJobCategories.systemAdministration";

    /**
     * Used for system administration jobs.
     */
    public static final String MONITORING = "$StandardJobCategories.monitoring";

    /**
     * Used for jobs which import/export or manage users.
     * <p>
     * Note that this will be used by the <tt>tenants</tt> framework, but can also be used by custom jobs and
     * user managers.
     */
    public static final String USERS_AND_TENANTS = "$StandardJobCategories.usersAndTenants";
}
