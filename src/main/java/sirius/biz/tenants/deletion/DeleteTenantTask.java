/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.deletion;

import sirius.biz.process.ProcessContext;
import sirius.biz.tenants.Tenant;
import sirius.kernel.async.TaskContext;
import sirius.kernel.di.std.Priorized;

/**
 * Invoked by the {@link DeleteTenantJobFactory delete tenant job} once a tenant is about to be deleted.
 * <p>
 * Can be used to remove related data before deleting the tenant itself. Note that the job supports a "simulation" run
 * which only outputs what would be deleted - without actually deleting anything.
 * <p>
 * Tasks are made visible by adding a {@link sirius.kernel.di.std.Register} annotation.
 */
public interface DeleteTenantTask extends Priorized {

    /**
     * Invoked before the handler itself ({@link #execute(ProcessContext, Tenant)} is called.
     * <p>
     * This should report how many objects will be deleted.
     *
     * @param process  the process to log to
     * @param tenant   the tenant which is about to be deleted
     * @param simulate <tt>true</tt> if this is a simulation run, <tt>false</tt> otherwise
     */
    void beforeExecution(ProcessContext process, Tenant<?> tenant, boolean simulate);

    /**
     * Called to actually perfrom the delete.
     * <p>
     * This will <b>not</b> be called in a simulation run.
     * <p>
     * Note that this method should invoke {@link ProcessContext#addTiming(String, long)} or
     * {@link sirius.kernel.async.TaskContext#tryUpdateState(String, Object...)}
     * in regular intervals whe processing large lists or data objects.
     *
     * @param process the process to log to
     * @param tenant  the tenant which is about to be deleted
     * @throws Exception if an error occurred and the job should be aborted
     * @see DeleteTenantJobFactory#TIMING_DELETED_ITEMS
     */
    void execute(ProcessContext process, Tenant<?> tenant) throws Exception;
}
