/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.cluster;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Contains infomation about all background jobs or processes running on a node.
 */
public class BackgroundInfo {
    private final String nodeName;
    private final boolean bleeding;
    private final String uptime;
    private final String version;
    protected final Map<String, BackgroundJobInfo> jobs = new HashMap<>();

    protected BackgroundInfo(String nodeName, boolean bleeding, String uptime, String version) {
        this.nodeName = nodeName;
        this.bleeding = bleeding;
        this.uptime = uptime;
        this.version = version;
    }

    /**
     * Returns the name of the node.
     *
     * @return the name of the node
     */
    public String getNodeName() {
        return nodeName;
    }

    /**
     * Returns the uptime of the node.
     *
     * @return the uptime of the node
     */
    public String getUptime() {
        return uptime;
    }

    public boolean isBleeding() {
        return bleeding;
    }

    public String getVersion() {
        return version;
    }

    /**
     * Provides a map of all background jobs running on a node.
     *
     * @return a map of all background jobs using the name as key
     */
    public Map<String, BackgroundJobInfo> getJobs() {
        return Collections.unmodifiableMap(jobs);
    }
}
