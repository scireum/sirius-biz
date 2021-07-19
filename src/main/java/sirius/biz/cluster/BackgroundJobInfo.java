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
    private final String name;
    private final String description;
    private final SynchronizeType synchronizeType;
    private final boolean localOverwrite;
    private final boolean globallyEnabled;
    private final String executionInfo;

    protected BackgroundJobInfo(String name,
                                String description,
                                SynchronizeType synchronizeType,
                                boolean localOverwrite,
                                boolean globallyEnabled,
                                String executionInfo) {
        this.name = name;
        this.description = description;
        this.synchronizeType = synchronizeType;
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
     * Returns the description of the job.
     *
     * @return a short description of the job
     */
    public String getDescription() {
        return description;
    }

    /**
     * Determines the synchronization settings for the job.
     *
     * @return the synchronization type to use
     */
    public SynchronizeType getSynchronizeType() {
        return synchronizeType;
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
