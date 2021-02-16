/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.ide;

/**
 * Wraps a transcript message which can be created by a running script and is available on all nodes.
 */
class TranscriptMessage {

    private final String node;
    private final String jobNumber;
    private final long timestamp;
    private final String message;

    protected TranscriptMessage(String node, String jobNumber, long timestamp, String message) {
        this.node = node;
        this.jobNumber = jobNumber;
        this.timestamp = timestamp;
        this.message = message;
    }

    public String getNode() {
        return node;
    }

    public String getJobNumber() {
        return jobNumber;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getMessage() {
        return message;
    }
}
