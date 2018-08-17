/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.cluster;

/**
 * Describes a background job or process running on a node.
 */
public class BackgroundJobInfo {
    private String name;
    private SynchronizeType local;
    private boolean localOverwrite;
    private boolean globallyEnabled;
    private String executionInfo;

    protected BackgroundJobInfo(String name,
                                SynchronizeType local,
                                boolean localOverwrite,
                                boolean globallyEnabled,
                                String executionInfo) {
        this.name = name;
        this.local = local;
        this.localOverwrite = localOverwrite;
        this.globallyEnabled = globallyEnabled;
        this.executionInfo = executionInfo;
    }

    /**
     * Returns the name of the job or process.
     *
     * @return the name of the background job
     */
    public String getName() {
        return name;
    }

    /**
     * Determines the local synchronization settings for the job.
     *
     * @return the local synchronization type to use
     */
    public SynchronizeType getLocal() {
        return local;
    }

    /**
     * Determines if the job if locally overwritten.
     * <p>
     * If this is <tt>true</tt>, the job is blocked for this node, even if it would be allowed to run, concerning the
     * system configuration.
     *
     * @return the local overwrite flag
     */
    public boolean isLocalOverwrite() {
        return localOverwrite;
    }

    /**
     * Determines if the job is globally enabled.
     *
     * @return <tt>true</tt> if the job is globally enabled, <tt>false</tt> otherwise
     */
    public boolean isGloballyEnabled() {
        return globallyEnabled;
    }

    /**
     * Returns the current or last known execution info for this job.
     *
     * @return a short string describing the last execution of this job on this node
     */
    public String getExecutionInfo() {
        return executionInfo;
    }
}
