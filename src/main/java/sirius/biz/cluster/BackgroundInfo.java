/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.cluster;

import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.health.Exceptions;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Contains information about all background jobs or processes running on a node.
 */
public class BackgroundInfo {

    @ConfigValue("product.baseUrl")
    private static String productBaseUrl;

    private final String nodeName;
    private final boolean bleeding;
    private final String uptime;
    private final String version;
    private final String detailedVersion;
    private final int activeBackgroundTasks;
    protected final Map<String, BackgroundJobInfo> jobs = new HashMap<>();

    protected BackgroundInfo(String nodeName,
                             boolean bleeding,
                             int activeBackgroundTasks,
                             String uptime,
                             String version,
                             String detailedVersion) {
        this.nodeName = nodeName;
        this.bleeding = bleeding;
        this.uptime = uptime;
        this.version = version;
        this.detailedVersion = detailedVersion;
        this.activeBackgroundTasks = activeBackgroundTasks;
    }

    /**
     * Builds the node-pinned URL for this node.
     *
     * @return the node-pinned URL for this node
     */
    public String buildNodeUrl() {
        try {
            URI baseUri = new URI(productBaseUrl);
            return new URI(baseUri.getScheme(),
                           baseUri.getUserInfo(),
                           nodeName + "." + baseUri.getHost(),
                           baseUri.getPort(),
                           null,
                           null,
                           null).toString();
        } catch (URISyntaxException exception) {
            Exceptions.ignore(exception);
        }
        return "";
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

    public String getDetailedVersion() {
        return detailedVersion;
    }

    public int getActiveBackgroundTasks() {
        return activeBackgroundTasks;
    }

    /**
     * Provides a map of all background jobs running on a node.
     *
     * @return a map of all background jobs using the name as key
     */
    public Map<String, BackgroundJobInfo> getJobs() {
        return Collections.unmodifiableMap(jobs);
    }

    /**
     * Checks if there is a valid value for the uptime.
     *
     * @return true if the value is not null and set to something different of "-"
     */
    public boolean hasUptime() {
        return uptime != null && !"-".equals(uptime);
    }
}
