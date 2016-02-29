/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.protocol;

import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.ExceptionHandler;
import sirius.kernel.health.Incident;
import sirius.kernel.health.LogMessage;
import sirius.kernel.health.LogTap;
import sirius.mixing.OMA;

/**
 * Created by aha on 18.02.16.
 */
@Register(classes = {Protocols.class, LogTap.class, ExceptionHandler.class})
public class Protocols implements LogTap, ExceptionHandler {

    @Part
    private OMA oma;

    @Override
    public void handle(Incident incident) throws Exception {

    }

    @Override
    public void handleLogMessage(LogMessage message) {
        if (!message.isReceiverWouldLog()) {
            return;
        }
    }
}
