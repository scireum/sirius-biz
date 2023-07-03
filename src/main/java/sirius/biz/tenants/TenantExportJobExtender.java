/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import sirius.biz.jobs.params.Parameter;
import sirius.biz.process.ProcessContext;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.query.Query;

import java.util.function.Consumer;

/**
 * Provides a base interface for extending job factories which export {@linkplain Tenant tenants}.
 *
 * @param <E> the type of tenants being exported
 * @param <Q> the query type used to select tenants
 */
public interface TenantExportJobExtender<E extends BaseEntity<?> & Tenant<?>, Q extends Query<Q, E, ?>> {

    /**
     * Collects all parameters expected by the tenant export job extension.
     *
     * @param parameterCollector the collector to be supplied with the expected parameters
     */
    default void collectParameters(Consumer<Parameter<?>> parameterCollector) {
        // empty as not every extender necessarily needs to add parameters
    }

    /**
     * Permits to add additional constraints on the query used to select exportable tenants.
     *
     * @param query          the query to extend
     * @param processContext the current process which can be used to extract parameters
     */
    void extendSelectQuery(Q query, ProcessContext processContext);
}
