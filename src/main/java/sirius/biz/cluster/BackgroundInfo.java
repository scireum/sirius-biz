/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.cluster;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Contains infomation about all background jobs or processes running on a node.
 */
public class BackgroundInfo {
    private String nodeName;
    private String uptime;
    protected List<BackgroundJobInfo> jobs = new ArrayList<>();

    protected BackgroundInfo(String nodeName, String uptime) {
        this.nodeName = nodeName;
        this.uptime = uptime;
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

    /**
     * Provides a list of all background jobs running on a node.
     *
     * @return a list of all background jobs
     */
    public List<BackgroundJobInfo> getJobs() {
        return Collections.unmodifiableList(jobs);
    }
}
