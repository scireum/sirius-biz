package sirius.biz.i5;

import com.ibm.as400.access.AS400;
import com.ibm.as400.access.AS400Message;
import com.ibm.as400.access.DataQueue;
import com.ibm.as400.access.DataQueueEntry;
import com.ibm.as400.access.ProgramCall;
import com.ibm.as400.access.ProgramParameter;
import sirius.kernel.async.Operation;
import sirius.kernel.commons.Watch;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class I5Connection {

    protected AS400 i5;
    protected I5ConnectionPool pool;
    protected boolean borrowed = false;
    protected long lastUse;
    protected String lastJob;

    public void release() {
        try {
            i5.disconnectAllServices();
        } catch (Throwable e) {
            throw Exceptions.handle()
                            .to(I5Connector.LOG)
                            .error(e)
                            .withSystemErrorMessage(
                                    "An error occurred while releasing an i5 connection of pool %s: %s (%s)",
                                    pool)
                            .handle();
        }
    }

    public void initialize() {
        try {
            if (i5.getVersion() < 6) {
                throw new IllegalStateException("We require at least V6R1M0 to run successfully");
            }

            pool.initConnection(this);
        } catch (Throwable e) {
            throw Exceptions.handle()
                            .to(I5Connector.LOG)
                            .error(e)
                            .withSystemErrorMessage(
                                    "An error occurred while creating an i5 connection for pool %s: %s (%s)",
                                    pool)
                            .handle();
        }
    }

    public boolean check() {
        return i5.isConnectionAlive(AS400.COMMAND);
    }

    public void close() {
        if (borrowed) {
            pool.returnConnection(this);
        }
    }

    public void call(String pgm, ProgramParameter... params) throws Exception {
        Watch w = Watch.start();
        lastUse = System.currentTimeMillis();
        try {
            ProgramCall p = new ProgramCall(i5, pgm, params);
            if (I5Connector.LOG.isFINE()) {
                I5Connector.LOG.FINE("PGM: " + pgm);
                I5Connector.LOG.FINE("JOB-ID: " + p.getServerJob());
                I5Connector.LOG.FINE("JOB-NAME: " + p.getServerJob().getName());
            }
            String currentJob = pgm + "/" + p.getServerJob() + " / " + p.getServerJob().getName();
            lastJob = currentJob;

            AtomicBoolean success = new AtomicBoolean();
            Operation.cover("i5", () -> currentJob, Duration.ofMinutes(1), () -> {
                try {
                    success.set(p.run());
                } catch (Throwable t) {
                    throw Exceptions.handle()
                                    .to(I5Connector.LOG)
                                    .error(t)
                                    .withSystemErrorMessage("Error while executing '%s': %s (%s)", currentJob)
                                    .handle();
                }
            });
            if (!success.get()) {
                StringBuilder err = new StringBuilder();
                for (AS400Message msg : p.getMessageList()) {
                    err.append(msg.getText());
                }

                throw Exceptions.handle()
                                .to(I5Connector.LOG)
                                .withSystemErrorMessage("Error while executing '%s': %s", currentJob, err.toString())
                                .handle();
            }
            pool.i5Connector.callUtilization.addValue(p.getServerJob().getCPUUsed());
            if (I5Connector.LOG.isFINE()) {
                StringBuilder sb = new StringBuilder();
                for (ProgramParameter param : params) {
                    if (param.getOutputDataLength() > 0) {
                        sb.append("[");
                        sb.append(new String(param.getOutputData()));
                        sb.append("]\n");
                    }
                }
                I5Connector.LOG.FINE("i5-Call OUTPUT: " + pgm + " - " + sb.toString());
            }
        } finally {
            w.submitMicroTiming("i5", "I5Connection.call#" + pgm);
            pool.i5Connector.calls.inc();
            pool.i5Connector.callDuration.addValue(w.elapsedMillis());
        }
    }

    public List<DataQueueEntry> readQueue(String queue, int timeoutSeconds) throws Exception {
        List<DataQueueEntry> result = new ArrayList<DataQueueEntry>();
        DataQueue q = new DataQueue(i5, queue);
        if (q != null) {
            DataQueueEntry entry = q.read(timeoutSeconds);
            while (entry != null) {
                result.add(entry);
                entry = q.read(timeoutSeconds);
            }
        }

        return result;
    }

    public String getLastUse() {
        return NLS.toUserString(Instant.ofEpochMilli(lastUse));
    }

    public String getLastJob() {
        return lastJob;
    }

    public String getBorrowed() {
        return NLS.toUserString(borrowed);
    }

    @Override
    public String toString() {
        return pool + ": " + i5;
    }
}
