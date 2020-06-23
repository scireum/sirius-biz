/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.protocol;

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
import sirius.kernel.nls.NLS;
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
    private RateLimit loggedMessageLimit = RateLimit.nTimesPerInterval(1, TimeUnit.MINUTES, 100);

    private AtomicLong disabledUntil;

    /**
     * In case the ES cluster is unreachable or we can for some reason not log errors or log messages,
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
            StoredIncident si = null;
            if (incident.getLocation() != null) {
                si = elastic.select(StoredIncident.class)
                            .eq(StoredIncident.LOCATION, incident.getLocation())
                            .where(Elastic.FILTERS.gt(StoredIncident.LAST_OCCURRENCE, yesterday))
                            .queryFirst();
            }

            if (si == null) {
                si = new StoredIncident();
                si.setLocation(incident.getLocation());
                si.setFirstOccurrence(LocalDateTime.now());
            }

            si.setNumberOfOccurrences(si.getNumberOfOccurrences() + 1);
            si.setNode(CallContext.getNodeName());
            for (Tuple<String, String> t : incident.getMDC()) {
                si.getMdc().put(t.getFirst(), t.getSecond());
            }
            si.setUser(UserContext.getCurrentUser().getProtocolUsername());
            si.setMessage(incident.getException().getMessage());
            si.setStack(NLS.toUserString(incident.getException()));
            si.setCategory(incident.getCategory());
            si.setLastOccurrence(LocalDateTime.now());

            elastic.update(si);
        } catch (Exception e) {
            Elastic.LOG.SEVERE(e);
            disableForOneMinute();
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
        } catch (Exception e) {
            Exceptions.ignore(e);
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
        } catch (Exception e) {
            Exceptions.handle(e);
        }
    }
}
