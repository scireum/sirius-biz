/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process.logs;

import sirius.biz.elastic.SearchableEntity;
import sirius.biz.process.Process;
import sirius.biz.process.Processes;
import sirius.biz.process.output.ProcessOutput;
import sirius.biz.process.output.ProcessOutputType;
import sirius.db.es.annotations.ESOption;
import sirius.db.es.annotations.IndexMode;
import sirius.db.es.types.ElasticRef;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.BeforeSave;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.annotations.Transient;
import sirius.db.mixing.types.BaseEntityRef;
import sirius.db.mixing.types.StringMap;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.GlobalContext;
import sirius.kernel.di.std.Framework;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.ExceptionHint;
import sirius.kernel.health.HandledException;
import sirius.kernel.nls.NLS;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Represents a log entry recorded for a {@link Process}.
 * <p>
 * This can be either a "plain" log entry or an element of a {@link ProcessOutput}
 * defined by the process.
 * <p>
 * Note that even a "plain" log message provides quite some functionality as it can specify a {@link ProcessLogHandler}
 * in {@link #handler} which can format the effective message to display and also provides possible actions for
 * it.
 * <p>
 * Also note that a log entry can be put into a {@link ProcessLogState} and therefore be used to keep track
 * of tasks / todos which were generated when executing the process.
 */
@Framework(Processes.FRAMEWORK_PROCESSES)
public class ProcessLog extends SearchableEntity {

    /**
     * Defines a limit to log messages for a {@link #messageType} to one.
     */
    public static final int MESSAGE_TYPE_COUNT_ONE = 1;

    /**
     * Defines a limit to log messages for a {@link #messageType} to 10.
     */
    public static final int MESSAGE_TYPE_COUNT_VERY_LOW = 10;

    /**
     * Defines a limit to log messages for a {@link #messageType} to 100.
     */
    public static final int MESSAGE_TYPE_COUNT_LOW = 100;

    /**
     * Defines a limit to log messages for a {@link #messageType} to 250.
     */
    public static final int MESSAGE_TYPE_COUNT_MEDIUM = 250;

    /**
     * Defines a limit to log messages for a {@link #messageType} to 1000.
     */
    public static final int MESSAGE_TYPE_COUNT_HIGH = 1000;

    /**
     * Hints the {@link ProcessLog#withMessageType(String)} to be used when handling an exception via
     * {@link #withHandledException(HandledException)}.
     */
    public static final ExceptionHint HINT_MESSAGE_TYPE = new ExceptionHint("messageType");

    /**
     * Hints the limit to be used for {@link ProcessLog#withLimitedMessageType(String, int)} to be used when handling
     * {@link #withHandledException(HandledException)}.
     */
    public static final ExceptionHint HINT_MESSAGE_COUNT = new ExceptionHint("messageCount");

    /**
     * Defines the name of the default action which simply toggles the state to {@link ProcessLogState#OPEN}.
     */
    public static final String ACTION_MARK_OPEN = "markOpen";

    /**
     * Defines the name of the default action which simply toggles the state to {@link ProcessLogState#RESOLVED}.
     */
    public static final String ACTION_MARK_RESOLVED = "markResolved";

    /**
     * Defines the name of the default action which simply toggles the state to {@link ProcessLogState#IGNORED}.
     */
    public static final String ACTION_MARK_IGNORED = "markIgnored";

    /**
     * Contains the process for which this log entry was created.
     */
    public static final Mapping PROCESS = Mapping.named("process");
    private final ElasticRef<Process> process = ElasticRef.writeOnceOn(Process.class, BaseEntityRef.OnDelete.IGNORE);

    /**
     * Contains the type or severity of this log entry.
     */
    public static final Mapping TYPE = Mapping.named("type");
    private ProcessLogType type = ProcessLogType.INFO;

    /**
     * Contains the timestamp when this log entry was recorded.
     */
    public static final Mapping TIMESTAMP = Mapping.named("timestamp");
    private LocalDateTime timestamp;

    /**
     * Contains the timestamp as plain long (with millisecond resolution).
     */
    public static final Mapping SORT_KEY = Mapping.named("sortKey");
    private Long sortKey;

    /**
     * Contains the node on which this log entry was recorded.
     */
    public static final Mapping NODE = Mapping.named("node");
    private String node;

    /**
     * Contains the name of the {@link ProcessOutput}
     * this entry belongs to.
     */
    public static final Mapping OUTPUT = Mapping.named("output");
    @NullAllowed
    private String output;

    /**
     * Contains the name of the {@link ProcessLogHandler} which is in charge of producing a
     * message (if empty) and also providing actions for this entry.
     */
    public static final Mapping MESSAGE_HANDLER = Mapping.named("messageHandler");
    @NullAllowed
    private String messageHandler;

    /**
     * Contains a custom message type which will be shown as filter using {@link NLS#smartGet(String)}.
     */
    public static final Mapping MESSAGE_TYPE = Mapping.named("messageType");
    @NullAllowed
    private String messageType;

    /**
     * Contains the task state of this log entry. Remain null if there is no and was never a task associated with
     * this entry.
     */
    public static final Mapping STATE = Mapping.named("state");
    @NullAllowed
    private ProcessLogState state;

    /**
     * Contains the context passed to the {@link ProcessLogHandler} or {@link ProcessOutput}
     */
    public static final Mapping CONTEXT = Mapping.named("context");
    private final StringMap context = new StringMap();

    /**
     * Determines if this is a system message which is not shown to "normal" users (ones which don't have
     * {@link sirius.biz.process.ProcessController#PERMISSION_MANAGE_ALL_PROCESSES}).
     */
    public static final Mapping SYSTEM_MESSAGE = Mapping.named("systemMessage");
    private boolean systemMessage;

    /**
     * Contains the log message to show. Can be <tt>null</tt> if either a {@link ProcessLogHandler} is
     * present to generate one or if the entry belongs to a {@link ProcessOutput}
     * which might only be interested in the {@link #context}.
     */
    public static final Mapping MESSAGE = Mapping.named("message");
    @IndexMode(indexed = ESOption.FALSE, docValues = ESOption.FALSE)
    @NullAllowed
    private String message;

    @Part
    private static GlobalContext ctx;

    @Transient
    private ProcessLogHandler handler;

    private int maxMessagesToLog;

    @BeforeSave
    protected void onSave() {
        StringBuilder searchContent = new StringBuilder();

        // We generate the message in the current language as this is still better to have a searchable content
        // in one language (which is most probably the correct language) than to skip the message entirely...
        searchContent.append(getMessage()).append(" ");

        // Append the content of each non-internal context field
        getContext().data().forEach((key, value) -> {
            if (!key.startsWith("_")) {
                searchContent.append(value).append(" ");
            }
        });

        setSearchableContent(searchContent.toString());
    }

    /**
     * Returns the timestamp formatted as string.
     *
     * @return the timestamp formatted as string
     */
    public String getTimestampAsString() {
        return Process.formatTimestamp(getTimestamp());
    }

    /**
     * Determines the color to be used for rendering the row of this log entry.
     *
     * @return the row color to be used for this log entry
     */
    public String getRowColor() {
        if (state == ProcessLogState.IGNORED) {
            return "gray";
        }
        if (type == ProcessLogType.ERROR) {
            return "red";
        }
        if (type == ProcessLogType.WARNING) {
            return "yellow";
        }
        if (type == ProcessLogType.SUCCESS) {
            return "green";
        }
        if (state == ProcessLogState.OPEN) {
            return "blue";
        }

        return "gray";
    }

    /**
     * Creates a log entry which is initialized with {@link ProcessLogType#INFO}.
     *
     * @return a new log entry with a pre-populated type
     */
    public static ProcessLog info() {
        return new ProcessLog().withType(ProcessLogType.INFO);
    }

    /**
     * Creates a log entry which is initialized with {@link ProcessLogType#WARNING}.
     *
     * @return a new log entry with a pre-populated type
     */
    public static ProcessLog warn() {
        return new ProcessLog().withType(ProcessLogType.WARNING);
    }

    /**
     * Creates a log entry which is initialized with {@link ProcessLogType#ERROR}.
     *
     * @return a new log entry with a pre-populated type
     */
    public static ProcessLog error() {
        return new ProcessLog().withType(ProcessLogType.ERROR);
    }

    /**
     * Creates a log entry which is initialized with {@link ProcessLogType#SUCCESS}.
     *
     * @return a new log entry with a pre-populated type
     */
    public static ProcessLog success() {
        return new ProcessLog().withType(ProcessLogType.SUCCESS);
    }

    /**
     * Specifies the type for this log entry.
     *
     * @param type the type of this log entry
     * @return the log entry itself for fluent method calls
     */
    public ProcessLog withType(ProcessLogType type) {
        this.type = type;
        return this;
    }

    /**
     * Specifies the state for this log entry.
     *
     * @param state the state of this log entry
     * @return the log entry itself for fluent method calls
     */
    public ProcessLog withState(ProcessLogState state) {
        this.state = state;
        return this;
    }

    /**
     * Marks this entry as unresolved task.
     *
     * @return the log entry itself for fluent method calls
     */
    public ProcessLog markOpen() {
        this.state = ProcessLogState.OPEN;
        return this;
    }

    /**
     * Specifies the {@link ProcessOutput} to which this log entry belogs.
     *
     * @param output the name of the output this entry belongs to
     * @return the log entry itself for fluent method calls
     */
    public ProcessLog into(String output) {
        this.output = output;
        return this;
    }

    /**
     * Specifies the {@link ProcessLogHandler} to use (either for generating a message on demand or for providing
     * {@link ProcessLogAction actions}.
     *
     * @param handler the handler to use for this entry.
     * @return the log entry itself for fluent method calls
     */
    public ProcessLog withMessageHandler(ProcessLogHandler handler) {
        this.messageHandler = handler.getName();
        return this;
    }

    /**
     * Specifies a custom message type to be used as facet filter.
     * <p>
     * Values will be {@link NLS#smartGet(String) smart translated}.
     *
     * @param messageType the custom message type to provide as filter value.
     * @return the log entry itself for fluent method calls
     */
    public ProcessLog withMessageType(String messageType) {
        this.messageType = messageType;
        return this;
    }

    /**
     * Specifies a custom message type to be used as facet filter while also limiting the maximal number of messages
     * recorded for this type.
     * <p>
     * Values will be {@link NLS#smartGet(String) smart translated}.
     *
     * @param messageType      the custom message type to provide as filter value.
     * @param maxMessagesToLog the maximal number of messages to log with this type. All other messages will be skipped,
     *                         but a counter will keep track of the total amount.
     * @return the log entry itself for fluent method calls
     */
    public ProcessLog withLimitedMessageType(String messageType, int maxMessagesToLog) {
        this.maxMessagesToLog = maxMessagesToLog;
        return withMessageType(messageType);
    }

    /**
     * Provides a context which is either supplied to the {@link ProcessLogHandler} or
     * {@link ProcessOutput} / {@link ProcessOutputType}.
     * <p>
     * Note that values which key start with an underscore are not added to the search index, everything else is
     * searchable.
     *
     * @param contextToAdd the context to add to the log entry
     * @return the log entry itself for fluent method calls
     */
    public ProcessLog withContext(Map<String, String> contextToAdd) {
        this.context.modify().putAll(contextToAdd);
        return this;
    }

    /**
     * Provides a name/value pair which is either supplied to the {@link ProcessLogHandler} or
     * {@link ProcessOutput} / {@link ProcessOutputType}.
     * <p>
     * Note that values which key start with an underscore are not added to the search index, everything else is
     * searchable.
     *
     * @param key   the key to add to the context
     * @param value the value to add to the context
     * @return the log entry itself for fluent method calls
     */
    public ProcessLog withContext(String key, Object value) {
        this.context.modify().put(key, NLS.toUserString(value));
        return this;
    }

    /**
     * Specifies the actual log message.
     *
     * @param message the message to log
     * @return the log entry itself for fluent method calls
     */
    public ProcessLog withMessage(String message) {
        this.message = message;
        return this;
    }

    /**
     * Specifies an NLS key which is then translated when the process log is rendered.
     *
     * @param key the NLS key to use as message
     * @return the log entry itself for fluent method calls
     */
    public ProcessLog withNLSKey(String key) {
        this.message = "$" + key;
        return this;
    }

    /**
     * Specifies a pattern and arguments to generate a formatted message just like
     * {@link Strings#apply(String, Object...)}.
     *
     * @param message    the pattern to use
     * @param parameters the arguments to supply to the pattern
     * @return the log entry itself for fluent method calls
     */
    public ProcessLog withFormattedMessage(String message, Object... parameters) {
        return withMessage(Strings.apply(message, parameters));
    }

    /**
     * Specifies a handled handledException from where to inherit the message.
     * <p>
     * If set, the {@link #HINT_MESSAGE_TYPE} is used to categorize the log in message types and
     * {@link #HINT_MESSAGE_COUNT} to limit the amount of allowed messages per type.
     *
     * @param handledException the {@link HandledException} to retrieve the message and eventually hints from
     * @return the log entry itself for fluent method calls
     */
    public ProcessLog withHandledException(HandledException handledException) {
        this.withMessage(handledException.getMessage());
        handledException.getHint(HINT_MESSAGE_TYPE).ifFilled(hintMessage -> {
            int messageCount = handledException.getHint(HINT_MESSAGE_COUNT).asInt(0);
            if (messageCount > 0) {
                this.withLimitedMessageType(hintMessage.getString(), messageCount);
            } else {
                this.withMessageType(hintMessage.getString());
            }
        });
        return this;
    }

    /**
     * Marks this message as {@link #SYSTEM_MESSAGE}.
     *
     * @return the log entry itself for fluent method calls
     */
    public ProcessLog asSystemMessage() {
        this.systemMessage = true;
        return this;
    }

    /**
     * Tries to determine the {@link ProcessLogHandler} which is in charge of this log entry.
     *
     * @return the handler wrapped as optional or an empty one if no handler is present
     */
    public Optional<ProcessLogHandler> getHandler() {
        if (handler == null && Strings.isFilled(messageHandler)) {
            handler = ctx.getPart(messageHandler, ProcessLogHandler.class);
        }

        return Optional.ofNullable(handler);
    }

    /**
     * Determines the log message to show when displaying this log entry.
     *
     * @return the effective log message of this entry
     */
    @SuppressWarnings("unchecked")
    public String getMessage() {
        if (Strings.isEmpty(message)) {
            return getHandler().map(h -> h.formatMessage(this)).orElse("");
        }

        if (message.startsWith("$")) {
            return NLS.fmtr(message.substring(1))
                      .set((Map<String, Object>) (Object) context.data())
                      .ignoreMissingParameters()
                      .format();
        }

        return message;
    }

    /**
     * Returns the action available for this log entry.
     *
     * @return a list of actions available for this entry
     */
    public List<ProcessLogAction> getActions() {
        return getHandler().map(h -> h.getActions(this)).orElseGet(this::getDefaultActions);
    }

    /**
     * Generates the default actions based on the state of this entry.
     *
     * @return the default actions available based on the state of this entry. Might be empty of the state is
     * <tt>null</tt>.
     */
    public List<ProcessLogAction> getDefaultActions() {
        if (state == null) {
            return Collections.emptyList();
        }

        List<ProcessLogAction> actions = new ArrayList<>(2);
        if (state != ProcessLogState.OPEN) {
            actions.add(new ProcessLogAction(this, ACTION_MARK_OPEN).withLabelKey("ProcessLog.actionMarkOpen")
                                                                    .withIcon("fa-retweet"));
        } else {
            actions.add(new ProcessLogAction(this, ACTION_MARK_RESOLVED).withLabelKey("ProcessLog.actionMarkResolved")
                                                                        .withIcon("fa-check-square-o "));
            actions.add(new ProcessLogAction(this, ACTION_MARK_IGNORED).withLabelKey("ProcessLog.actionMarkIgnored")
                                                                       .withIcon("fa-ban"));
        }

        return actions;
    }

    @Override
    public String toString() {
        return NLS.fmtr("ProcessLog.format")
                  .set("process", getProcess().getId())
                  .set("timestamp", NLS.toUserString(getTimestamp()))
                  .format();
    }

    public String getOutput() {
        return output;
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

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getNode() {
        return node;
    }

    public void setNode(String node) {
        this.node = node;
    }

    public ProcessLogState getState() {
        return state;
    }

    public StringMap getContext() {
        return context;
    }

    public void setSortKey(Long sortKey) {
        this.sortKey = sortKey;
    }

    public Long getSortKey() {
        return sortKey;
    }

    public String getMessageType() {
        return messageType;
    }

    public int getMaxMessagesToLog() {
        return maxMessagesToLog;
    }
}
