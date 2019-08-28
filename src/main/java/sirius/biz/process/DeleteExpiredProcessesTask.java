/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process;

import sirius.biz.process.logs.ProcessLog;
import sirius.db.es.Elastic;
import sirius.kernel.Sirius;
import sirius.kernel.async.Tasks;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.timer.EveryDay;

import java.time.LocalDate;

/**
 * Deletes expired {@link Process processes} and {@link ProcessLog logs} of {@link ProcessState#STANDBY standby}
 * processes.
 */
@Register(framework = Processes.FRAMEWORK_PROCESSES)
public class DeleteExpiredProcessesTask implements EveryDay {

    @Part
    private Elastic elastic;

    @Part
    private Tasks tasks;

    @Override
    public String getConfigKeyName() {
        return "cleanup-processes";
    }

    @Override
    public void runTimer() throws Exception {
        if (elastic != null && elastic.getReadyFuture().isCompleted() && !Sirius.isStartedAsTest()) {
            tasks.defaultExecutor().fork(this::clenaup);
        }
    }

    private void clenaup() {
        elastic.select(Process.class)
               .eq(Process.STATE, ProcessState.TERMINATED)
               .where(Elastic.FILTERS.lte(Process.EXPIRES, LocalDate.now()))
               .delete();

        elastic.select(Process.class)
               .eq(Process.STATE, ProcessState.STANDBY)
               .iterateAll(this::deleteExpiredStandbyLogs);
    }

    private void deleteExpiredStandbyLogs(Process process) {
        LocalDate limit = process.getPersistencePeriod().minus(LocalDate.now());
        elastic.select(ProcessLog.class)
               .eq(ProcessLog.PROCESS, process)
               .where(Elastic.FILTERS.lt(ProcessLog.TIMESTAMP, limit))
               .delete();
    }
}
