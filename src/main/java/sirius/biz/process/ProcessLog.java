/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process;

import sirius.biz.elastic.SearchContent;
import sirius.biz.elastic.SearchableEntity;
import sirius.db.es.annotations.ESOption;
import sirius.db.es.annotations.IndexMode;
import sirius.db.es.types.ElasticRef;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.types.BaseEntityRef;
import sirius.kernel.commons.Strings;
import sirius.kernel.nls.NLS;

import java.time.LocalDateTime;

public class ProcessLog extends SearchableEntity {

    public static final Mapping PROCESS = Mapping.named("process");
    private final ElasticRef<Process> process = ElasticRef.on(Process.class, BaseEntityRef.OnDelete.CASCADE);

    public static final Mapping TYPE = Mapping.named("type");
    private ProcessLogType type;

    public static final Mapping TIMESTAMP = Mapping.named("timestamp");
    private LocalDateTime timestamp;

    public static final Mapping NODE = Mapping.named("node");
    private String node;

    public static final Mapping MESSAGE_TYPE = Mapping.named("messageType");
    @NullAllowed
    private String messageType;

    public static final Mapping SYSTEM_MESSAGE = Mapping.named("systemMessage");
    private boolean systemMessage;

    //TODO add problems here ;-)

    public static final Mapping MESSAGE = Mapping.named("message");
    @SearchContent
    @IndexMode(indexed = ESOption.FALSE, docValues = ESOption.FALSE)
    private String message;

    /**
     * Determines the bootstrap CSS class to be used for rendering the row of this process.
     */
    public String getRowClass() {
        if (type == ProcessLogType.ERROR) {
            return "danger";
        }
        if (type == ProcessLogType.WARNING) {
            return "warning";
        }
        if (type == ProcessLogType.SUCCESS) {
            return "success";
        }

        return "";
    }

    public static ProcessLog info(String message, Object... parameters) {
        return new ProcessLog().withType(ProcessLogType.INFO).withMessage(message, parameters);
    }

    public static ProcessLog warn(String message, Object... parameters) {
        return new ProcessLog().withType(ProcessLogType.WARNING).withMessage(message, parameters);
    }

    public static ProcessLog error(String message, Object... parameters) {
        return new ProcessLog().withType(ProcessLogType.ERROR).withMessage(message, parameters);
    }

    public static ProcessLog success(String message, Object... parameters) {
        return new ProcessLog().withType(ProcessLogType.SUCCESS).withMessage(message, parameters);
    }

    public ProcessLog withType(ProcessLogType type) {
        this.type = type;
        return this;
    }

    public ProcessLog withMessageType(String messageType) {
        this.messageType = messageType;
        return this;
    }

    public ProcessLog withMessage(String message) {
        this.message = message;
        return this;
    }

    public ProcessLog withMessage(String message, Object... parameters) {
        return withMessage(Strings.apply(message, parameters));
    }

    public ProcessLog asSystemMessage() {
        this.systemMessage = true;
        return this;
    }

    public boolean isSystemMessage() {
        return systemMessage;
    }

    public ElasticRef<Process> getProcess() {
        return process;
    }

    public ProcessLogType getType() {
        return type;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    protected void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getNode() {
        return node;
    }

    protected void setNode(String node) {
        this.node = node;
    }

    public String getMessageType() {
        return messageType;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return NLS.toUserString(timestamp) + " (" + node + ") - " + type + ": " + message;
    }
}
