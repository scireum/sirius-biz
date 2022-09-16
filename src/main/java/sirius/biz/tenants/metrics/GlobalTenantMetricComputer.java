/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.metrics;

/**
 * Provides some common constants used by both, {@link sirius.biz.tenants.jdbc.SQLTenantGlobalMetricComputer}
 * and {@link sirius.biz.tenants.mongo.MongoTenantGlobalMetricComputer}.
 */
public class GlobalTenantMetricComputer {

    private GlobalTenantMetricComputer() {
    }

    /**
     * Contains the total number of tenants.
     */
    public static final String METRIC_NUM_TENANTS = "num-tenants";

    /**
     * Contains the number of tenants with active users.
     */
    public static final String METRIC_NUM_ACTIVE_TENANTS = "num-active-tenants";

    /**
     * Contains the total number of users.
     */
    public static final String METRIC_NUM_USERS = "num-users";

    /**
     * Contains the number of active users.
     */
    public static final String METRIC_NUM_ACTIVE_USERS = "num-active-users";
}
