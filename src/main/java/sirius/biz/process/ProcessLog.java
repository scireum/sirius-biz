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
import sirius.db.mixing.annotations.Transient;
import sirius.db.mixing.types.BaseEntityRef;
import sirius.db.mixing.types.StringMap;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.GlobalContext;
import sirius.kernel.di.std.Part;
import sirius.kernel.nls.NLS;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ProcessLog extends SearchableEntity {

    public static final Mapping PROCESS = Mapping.named("process");
    private final ElasticRef<Process> process = ElasticRef.on(Process.class, BaseEntityRef.OnDelete.CASCADE);

    public static final Mapping TYPE = Mapping.named("type");
    private ProcessLogType type;

    public static final Mapping TIMESTAMP = Mapping.named("timestamp");
    private LocalDateTime timestamp;

    public static final Mapping NODE = Mapping.named("node");
    private String node;

    public static final Mapping OUTPUT_TABLE = Mapping.named("outputTable");
    @NullAllowed
    private String outputTable;

    public static final Mapping MESSAGE_HANDLER = Mapping.named("messageHandler");
    @NullAllowed
    private String messageHandler;

    public static final Mapping STATE = Mapping.named("state");
    @NullAllowed
    private ProcessLogState state;

    public static final Mapping CONTEXT = Mapping.named("context");
    private final StringMap context = new StringMap();

    public static final Mapping SYSTEM_MESSAGE = Mapping.named("systemMessage");
    private boolean systemMessage;

    public static final Mapping MESSAGE = Mapping.named("message");
    @SearchContent
    @IndexMode(indexed = ESOption.FALSE, docValues = ESOption.FALSE)
    @NullAllowed
    private String message;

    @Part
    private static GlobalContext ctx;

    @Transient
    private ProcessLogHandler handler;

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

        return "default";
    }

    public static ProcessLog info() {
        return new ProcessLog().withType(ProcessLogType.INFO);
    }

    public static ProcessLog warn() {
        return new ProcessLog().withType(ProcessLogType.WARNING);
    }

    public static ProcessLog error() {
        return new ProcessLog().withType(ProcessLogType.ERROR);
    }

    public static ProcessLog success() {
        return new ProcessLog().withType(ProcessLogType.SUCCESS);
    }

    public ProcessLog withType(ProcessLogType type) {
        this.type = type;
        return this;
    }

    public ProcessLog into(String outputTable) {
        this.outputTable = outputTable;
        return this;
    }
    public ProcessLog withMessageHandler(ProcessLogHandler handler) {
        this.messageHandler = handler.getName();
        return this;
    }

    public ProcessLog withContext(Map<String, String> contextToAdd) {
        this.context.modify().putAll(contextToAdd);
        return this;
    }

    public ProcessLog withContext(String key, String value) {
        this.context.modify().put(key, value);
        return this;
    }

    public ProcessLog withMessage(String message) {
        this.message = message;
        return this;
    }

    public ProcessLog withNLSKey(String key) {
        this.message = "$" + key;
        return this;
    }

    public ProcessLog withFormattedMessage(String message, Object... parameters) {
        return withMessage(Strings.apply(message, parameters));
    }

    public ProcessLog asSystemMessage() {
        this.systemMessage = true;
        return this;
    }

    public Optional<ProcessLogHandler> getHandler() {
        if (handler == null && Strings.isFilled(messageHandler)) {
            handler = ctx.findPart(messageHandler, ProcessLogHandler.class);
        }

        return Optional.ofNullable(handler);
    }

    public String getOutputTable() {
        return outputTable;
    }

    @SuppressWarnings("unchecked")
    public String getMessage() {
        if (Strings.isEmpty(message)) {
            return getHandler().map(h -> h.formatMessage(this)).orElse("");
        }

        if (message.startsWith("$")) {
            return NLS.fmtr(message.substring(1)).set((Map<String, Object>) (Object) context.data()).format();
        }

        return message;
    }

    public List<Tuple<String, String>> getActions() {
        return getHandler().map(h -> h.getActions(this)).orElse(Collections.emptyList());
    }

    @Override
    public String toString() {
        return NLS.toUserString(timestamp) + " (" + node + ") - " + type + ": " + message;
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
}
