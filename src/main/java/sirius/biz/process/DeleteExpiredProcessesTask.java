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
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.kernel.timer.EndOfDayTask;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Deletes expired {@link Process processes} and {@link ProcessLog logs} of {@link ProcessState#STANDBY standby}
 * processes.
 */
@Register(framework = Processes.FRAMEWORK_PROCESSES)
public class DeleteExpiredProcessesTask implements EndOfDayTask {

    @Part
    private Elastic elastic;

    @Part
    private Tasks tasks;

    @Override
    public String getName() {
        return "cleanup-processes";
    }

    @Override
    public void execute() throws Exception {
        if (elastic != null && elastic.getReadyFuture().isCompleted() && !Sirius.isStartedAsTest()) {
            this.cleanup();
        }
    }

    private void cleanup() {
        elastic.select(Process.class)
               .eq(Process.STATE, ProcessState.TERMINATED)
               .where(Elastic.FILTERS.lte(Process.EXPIRES, LocalDate.now()))
               .streamBlockwise()
               .forEach(this::deleteProcess);

        elastic.select(Process.class)
               .eq(Process.STATE, ProcessState.STANDBY)
               .streamBlockwise()
               .forEach(this::deleteExpiredStandbyLogs);
    }

    private void deleteProcess(Process process) {
        try {
            elastic.delete(process);
        } catch (HandledException exception) {
            // The delete-by-query might fail if too many logs exist. The deletion in ElasticSearch might still
            // be running, it could also have stopped. We ignore this error here, the process will remain in the
            // database and the deletion will be retried in the next run.
            Exceptions.ignore(exception);
        }
    }

    private void deleteExpiredStandbyLogs(Process process) {
        LocalDate limit = Optional.ofNullable(process.getPersistencePeriod())
                                  .orElse(PersistencePeriod.THREE_MONTHS)
                                  .minus(LocalDate.now());
        elastic.select(ProcessLog.class)
               .eq(ProcessLog.PROCESS, process)
               .where(Elastic.FILTERS.lt(ProcessLog.TIMESTAMP, limit))
               .delete();
    }
}
