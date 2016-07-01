/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.protocol;

import sirius.db.mixing.OMA;
import sirius.db.mixing.OptimisticLockException;
import sirius.db.mixing.constraints.FieldOperator;
import sirius.kernel.Sirius;
import sirius.kernel.async.CallContext;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.ExceptionHandler;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.LogMessage;
import sirius.kernel.health.LogTap;
import sirius.kernel.nls.NLS;
import sirius.web.security.UserContext;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * Created by aha on 18.02.16.
 */
@Register(classes = {Protocols.class, LogTap.class, ExceptionHandler.class}, framework = Protocols.FRAMEWORK_PROTOCOLS)
public class Protocols implements LogTap, ExceptionHandler {

    public static final String FRAMEWORK_PROTOCOLS = "protocols";
    public static final String PERMISSION_VIEW_PROTOCOLS = "permission-view-protocols";

    @Part
    private OMA oma;

    @Override
    public void handle(sirius.kernel.health.Incident incident) throws Exception {
        try {
            if (oma == null || !oma.isReady() || Sirius.isStartedAsTest()) {
                return;
            }
            try {
                LocalDate yesterday = LocalDate.now().minusDays(1);
                Incident si = null;
                if (incident.getLocation() != null) {
                    si = oma.select(Incident.class)
                            .eq(Incident.LOCATION, incident.getLocation())
                            .where(FieldOperator.on(Incident.LAST_OCCURRENCE).greaterThan(yesterday))
                            .queryFirst();
                }
                if (si == null) {
                    si = new Incident();
                    si.setLocation(incident.getLocation());
                    si.setFirstOccurrence(LocalDateTime.now());
                }
                si.setNumberOfOccurrences(si.getNumberOfOccurrences() + 1);
                si.setNode(CallContext.getNodeName());

                si.setMdc(incident.getMDC().stream().map(Tuple::toString).collect(Collectors.joining("\n")));
                si.setUser(UserContext.getCurrentUser().getUserName());
                si.setMessage(incident.getException().getMessage());
                si.setStack(NLS.toUserString(incident.getException()));
                si.setCategory(incident.getCategory());
                si.setLastOccurrence(LocalDateTime.now());

                oma.tryUpdate(si);
            } catch (OptimisticLockException e) {
                Exceptions.ignore(e);
            }
        } catch (Throwable e) {
            Exceptions.handle(e);
        }
    }

    @Override
    public void handleLogMessage(LogMessage message) {
        if (oma == null
            || message == null
            || !oma.isReady()
            || !message.isReceiverWouldLog()
            || Sirius.isStartedAsTest()) {
            return;
        }

        LogEntry entry = new LogEntry();
        entry.setCategory(message.getReceiver().getName());
        entry.setLevel(message.getLogLevel().toString());
        entry.setMessage(message.getMessage());
        entry.setNode(CallContext.getNodeName());
        entry.setTod(LocalDateTime.now());
        entry.setUser(UserContext.getCurrentUser().getUserName());

        oma.update(entry);
    }
}
