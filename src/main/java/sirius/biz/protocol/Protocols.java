/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.protocol;

import com.google.common.base.Throwables;
import sirius.biz.elastic.AutoBatchLoop;
import sirius.db.es.Elastic;
import sirius.kernel.Sirius;
import sirius.kernel.async.CallContext;
import sirius.kernel.commons.RateLimit;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.ExceptionHandler;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Incident;
import sirius.kernel.health.LogMessage;
import sirius.kernel.health.LogTap;
import sirius.web.mails.MailLog;
import sirius.web.security.UserContext;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/**
 * Adapter which records all log entries and incidents and mails into the appropriate database entities.
 */
@Register(classes = {Protocols.class, LogTap.class, ExceptionHandler.class, MailLog.class},
        framework = Protocols.FRAMEWORK_PROTOCOLS)
public class Protocols implements LogTap, ExceptionHandler, MailLog {

    private static final int DISABLE_ON_ERROR_PERIOD_MILLIS = 1000 * 60;

    private static final int NUMBER_OF_CHARS_TO_PRESERVE_AT_THE_END_OF_AN_ERROR_MESSAGE = 1000;

    /**
     * Names the framework which must be enabled to activate all protocol features.
     */
    public static final String FRAMEWORK_PROTOCOLS = "biz.protocols";

    /**
     * Names the framework which must be enabled to activate the system journal.
     */
    public static final String FRAMEWORK_JOURNAL = "biz.journal";

    /**
     * Names the permissions required to view the protocol.
     */
    public static final String PERMISSION_SYSTEM_PROTOCOLS = "permission-system-protocols";

    /**
     * Names the permissions required to view the system journal.
     */
    public static final String PERMISSION_SYSTEM_JOURNAL = "permission-system-journal";

    @Part
    private Elastic elastic;

    @Part
    private AutoBatchLoop autoBatch;

    @ConfigValue("protocols.maxLogMessageLength")
    private int maxMessageLength;

    /**
     * To prevent overrunning Elasticsearch by logging messages, we limit the number of logged messages
     * to 100/min.
     */
    private final RateLimit loggedMessageLimit = RateLimit.nTimesPerInterval(1, TimeUnit.MINUTES, 100);

    private AtomicLong disabledUntil;

    /**
     * In case the ES cluster is unreachable, or we can for some reason not log errors or log messages,
     * we disable the facility for one minute so that the local syslogs aren't jammed with errors.
     */
    private void disableForOneMinute() {
        disabledUntil = new AtomicLong(System.currentTimeMillis() + DISABLE_ON_ERROR_PERIOD_MILLIS);
    }

    private boolean isDisabled() {
        if (disabledUntil == null) {
            return false;
        }

        if (disabledUntil.get() < System.currentTimeMillis()) {
            disabledUntil = null;
            return false;
        }

        return true;
    }

    @Override
    public void handle(Incident incident) throws Exception {
        if (elastic == null || !elastic.getReadyFuture().isCompleted() || Sirius.isStartedAsTest() || isDisabled()) {
            return;
        }

        try {
            LocalDate yesterday = LocalDate.now().minusDays(1);
            StoredIncident storedIncident = null;
            if (incident.getLocation() != null) {
                storedIncident = elastic.select(StoredIncident.class)
                                        .eq(StoredIncident.LOCATION, incident.getLocation())
                                        .where(Elastic.FILTERS.gt(StoredIncident.LAST_OCCURRENCE, yesterday))
                                        .queryFirst();
            }

            if (storedIncident == null) {
                storedIncident = new StoredIncident();
                storedIncident.setLocation(incident.getLocation());
                storedIncident.setFirstOccurrence(LocalDateTime.now());
            }

            storedIncident.setNumberOfOccurrences(storedIncident.getNumberOfOccurrences() + 1);
            storedIncident.setNode(CallContext.getNodeName());
            for (Tuple<String, String> tuple : incident.getMDC()) {
                storedIncident.getMdc().put(tuple.getFirst(), tuple.getSecond());
            }
            storedIncident.setUser(UserContext.getCurrentUser().getProtocolUsername());
            storedIncident.setMessage(buildErrorMessages(incident.getException()));
            storedIncident.setStack(Exceptions.buildStackTraceWithoutErrorMessage(incident.getException()));
            storedIncident.setCategory(incident.getCategory());
            storedIncident.setLastOccurrence(LocalDateTime.now());

            elastic.update(storedIncident);
        } catch (Exception exception) {
            Elastic.LOG.SEVERE(exception);
            disableForOneMinute();
        }
    }

    private String buildErrorMessages(Throwable throwable) {

        StringBuilder stringBuilder = new StringBuilder();

        int numberOfCharactersPerMessage = calcCharactersPerMessage(throwable);

        stringBuilder.append(throwable.getClass().getName()).append(":").append("\n");
        stringBuilder.append(truncateErrorMessage(throwable.getMessage(), numberOfCharactersPerMessage)).append("\n");

        try {
            // The first element of the causal chain is always the throwable followed by its cause hierarchy.
            // Therefore, the first element is skipped.
            Throwables.getCausalChain(throwable).stream().skip(1).forEach(cause -> {
                stringBuilder.append("\n").append("Caused by: ").append(cause.getClass().getName()).append(":").append("\n");
                stringBuilder.append(truncateErrorMessage(determineErrorMessage(cause), numberOfCharactersPerMessage)).append("\n\n");
            });
        } catch (IllegalArgumentException exception) {
            // This happens if the causal chain has a circular reference.
            stringBuilder.append("Warning: Circular reference detected in causal chain. Skipping causes: ")
                         .append(exception.getMessage());
        }

        return stringBuilder.toString();
    }

    private String determineErrorMessage(Throwable throwable) {
        if (Strings.isFilled(throwable.getMessage())) {
            return throwable.getMessage();
        }
        if (Strings.areEqual(throwable.getClass().getName(), throwable.toString())) {
            return "";
        }
        return throwable.toString();
    }

    private String truncateErrorMessage(String errorMessage, int length) {
        int charsToPreserveFromStart = Math.max(0, length - NUMBER_OF_CHARS_TO_PRESERVE_AT_THE_END_OF_AN_ERROR_MESSAGE);
        return Strings.truncateMiddle(errorMessage, charsToPreserveFromStart, NUMBER_OF_CHARS_TO_PRESERVE_AT_THE_END_OF_AN_ERROR_MESSAGE);
    }

    private int calcCharactersPerMessage(Throwable throwable) {
        try {
            return maxMessageLength / Throwables.getCausalChain(throwable).size();
        } catch (IllegalArgumentException ignored) {
            return maxMessageLength;
        }
    }

    @Override
    public void handleLogMessage(LogMessage message) {
        if (shouldNotLog(message)) {
            return;
        }

        try {
            LoggedMessage msg = new LoggedMessage();
            msg.setCategory(message.getReceiver().getName());
            msg.setLevel(determineLevel(message.getLogLevel()));
            msg.setMessage(Strings.limit(message.getMessage(), maxMessageLength, false));
            msg.setNode(CallContext.getNodeName());
            msg.setUser(UserContext.getCurrentUser().getProtocolUsername());

            autoBatch.insertAsync(msg);
        } catch (Exception exception) {
            Exceptions.ignore(exception);
            disableForOneMinute();
        }
    }

    private String determineLevel(Level logLevel) {
        if (Level.SEVERE.equals(logLevel)) {
            return "ERROR";
        }
        if (Level.WARNING.equals(logLevel)) {
            return "WARN";
        }
        if (Level.INFO.equals(logLevel)) {
            return "INFO";
        }

        return "DEBUG";
    }

    protected boolean shouldNotLog(LogMessage message) {
        if (!Sirius.isRunning() || Sirius.isStartedAsTest()) {
            return true;
        }

        if (message == null) {
            return true;
        }

        if (elastic == null || !elastic.getReadyFuture().isCompleted()) {
            return true;
        }

        if (!loggedMessageLimit.check()) {
            return true;
        }

        return isDisabled();
    }

    @Override
    public void logSentMail(boolean success,
                            String messageId,
                            String sender,
                            String senderName,
                            String receiver,
                            String receiverName,
                            String subject,
                            String text,
                            String html,
                            String type) {
        if (elastic == null || !elastic.getReadyFuture().isCompleted() || Sirius.isStartedAsTest()) {
            return;
        }

        try {
            MailProtocol msg = new MailProtocol();
            msg.setTod(LocalDateTime.now());
            msg.setMessageId(messageId);
            msg.setSender(sender);
            msg.setSenderName(senderName);
            msg.setReceiver(receiver);
            msg.setReceiverName(receiverName);
            msg.setSubject(subject);
            msg.setTextContent(text);
            msg.setHtmlContent(html);
            msg.setSuccess(success);
            msg.setNode(CallContext.getNodeName());
            msg.setType(type);

            elastic.update(msg);
        } catch (Exception exception) {
            Exceptions.handle(exception);
        }
    }
}
