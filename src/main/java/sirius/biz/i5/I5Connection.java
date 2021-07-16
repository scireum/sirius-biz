/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

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
import sirius.kernel.health.HandledException;
import sirius.kernel.nls.NLS;

import javax.annotation.Nonnull;
import java.io.Closeable;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a connection to an IBM AS400 also known as i5.
 */
public class I5Connection implements Closeable {

    protected AS400 i5;
    protected I5ConnectionPool pool;
    protected boolean borrowed = false;
    protected long lastUse;
    protected String lastJob;

    /**
     * Releases / closes the connection.
     */
    public void release() {
        try {
            i5.disconnectAllServices();
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(I5Connector.LOG)
                            .error(e)
                            .withSystemErrorMessage(
                                    "An error occurred while releasing an i5 connection of pool %s: %s (%s)",
                                    pool)
                            .handle();
        }
    }

    /**
     * Initializes the connection and verifies the installed version of OS400.
     */
    public void initialize() {
        try {
            if (i5.getVersion() < 6) {
                throw new IllegalStateException("We require at least V6R1M0 to run successfully");
            }

            pool.initConnection(this);
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(I5Connector.LOG)
                            .error(e)
                            .withSystemErrorMessage(
                                    "An error occurred while creating an i5 connection for pool %s: %s (%s)",
                                    pool)
                            .handle();
        }
    }

    /**
     * Checks if the connection is still healthy.
     *
     * @return <tt>true</tt> if the connection is still healthy, false otherwise.
     */
    public boolean check() {
        return i5.isConnectionAlive(AS400.COMMAND);
    }

    /**
     * Closes the connection by returning it back to the connection pool.
     */
    @Override
    public void close() {
        if (borrowed) {
            pool.returnConnection(this);
        }
    }

    /**
     * Calls a program on the i5.
     *
     * @param pgm    the name of the program to call
     * @param params the parameters to pass
     */
    public void call(String pgm, ProgramParameter... params) {
        Watch w = Watch.start();
        lastUse = System.currentTimeMillis();
        try {
            ProgramCall p = new ProgramCall(i5, pgm, params);
            String currentJob = getCurrentJob(pgm, p);
            lastJob = currentJob;

            executeProgramCall(p, currentJob);

            collectCPUUsed(p, pgm);
            if (I5Connector.LOG.isFINE()) {
                logProgramOutput(pgm, params);
            }
        } finally {
            w.submitMicroTiming("i5", "I5Connection.call#" + pgm);
            pool.i5Connector.calls.inc();
            pool.i5Connector.callDuration.addValue(w.elapsedMillis());
        }
    }

    private void logProgramOutput(String pgm, ProgramParameter[] params) {
        StringBuilder sb = new StringBuilder();
        for (ProgramParameter param : params) {
            if (param.getOutputDataLength() > 0) {
                sb.append("[");
                sb.append(new String(param.getOutputData()));
                sb.append("]\n");
            }
        }
        I5Connector.LOG.FINE("i5-Call OUTPUT: " + pgm + " - " + sb);
    }

    private void executeProgramCall(ProgramCall p, String currentJob) {
        try (Operation op = new Operation(() -> currentJob, Duration.ofMinutes(1))) {
            if (!p.run()) {
                StringBuilder err = new StringBuilder();
                for (AS400Message msg : p.getMessageList()) {
                    err.append(msg.getText());
                }

                throw Exceptions.handle()
                                .to(I5Connector.LOG)
                                .withSystemErrorMessage("Error while executing '%s': %s", currentJob, err.toString())
                                .handle();
            }
        } catch (HandledException t) {
            throw t;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw Exceptions.handle()
                            .to(I5Connector.LOG)
                            .error(exception)
                            .withSystemErrorMessage("The thread was interrupted while executing '%s': %s (%s)",
                                                    currentJob)
                            .handle();
        } catch (Exception exception) {
            throw Exceptions.handle()
                            .to(I5Connector.LOG)
                            .error(exception)
                            .withSystemErrorMessage("Error while executing '%s': %s (%s)", currentJob)
                            .handle();
        }
    }

    private String getCurrentJob(String pgm, ProgramCall p) {
        try {
            if (I5Connector.LOG.isFINE()) {
                I5Connector.LOG.FINE("PGM: " + pgm);
                I5Connector.LOG.FINE("JOB-ID: " + p.getServerJob());
                I5Connector.LOG.FINE("JOB-NAME: " + p.getServerJob().getName());
            }
            return pgm + "/" + p.getServerJob() + " / " + p.getServerJob().getName();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw Exceptions.handle()
                            .to(I5Connector.LOG)
                            .error(exception)
                            .withSystemErrorMessage("Got interrupted while calling i5: %s (%s)")
                            .handle();
        } catch (Exception exception) {
            throw Exceptions.handle()
                            .to(I5Connector.LOG)
                            .error(exception)
                            .withSystemErrorMessage("Error while calling i5: %s (%s)")
                            .handle();
        }
    }

    private void collectCPUUsed(ProgramCall p, String currentJob) {
        try {
            pool.i5Connector.callUtilization.addValue(p.getServerJob().getCPUUsed());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw Exceptions.handle()
                            .to(I5Connector.LOG)
                            .error(exception)
                            .withSystemErrorMessage("Got interrupted while executing '%s': %s (%s)", currentJob)
                            .handle();
        } catch (Exception exception) {
            throw Exceptions.handle()
                            .to(I5Connector.LOG)
                            .error(exception)
                            .withSystemErrorMessage("Error while executing '%s': %s (%s)", currentJob)
                            .handle();
        }
    }

    /**
     * Reads all entries available in the given queue.
     *
     * @param queue          the name of the queue to read
     * @param timeoutSeconds the max timeout to wait for entries
     * @return the list of entries read
     * @throws Exception in case of a communication or host error
     */
    @Nonnull
    public List<DataQueueEntry> readQueue(String queue, int timeoutSeconds) throws Exception {
        List<DataQueueEntry> result = new ArrayList<>();
        DataQueue q = new DataQueue(i5, queue);
        DataQueueEntry entry = q.read(timeoutSeconds);
        while (entry != null) {
            result.add(entry);
            entry = q.read(timeoutSeconds);
        }

        return result;
    }

    /**
     * Returns the timestamp of the last usage of this connection.
     *
     * @return the last time the connection was used as formatted timestamp
     */
    public String getLastUse() {
        return NLS.toUserString(Instant.ofEpochMilli(lastUse));
    }

    /**
     * The id of the last job executed on the host
     *
     * @return the id of the last job
     */
    public String getLastJob() {
        return lastJob;
    }

    /**
     * Determines if the connection is currently borrowed or not.
     *
     * @return <tt>true</tt> if the connection is currently in use, <tt>false</tt> if it is idle in a connection pool
     */
    public boolean isBorrowed() {
        return borrowed;
    }

    @Override
    public String toString() {
        return pool + ": " + i5;
    }

    /**
     * Returns the CCSID (code page) used by the i5 at the other end.
     *
     * @return the CCSID to use when encoding or decoding strings
     */
    public int getCcsid() {
        return i5.getCcsid();
    }
}
