/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.events;

import sirius.kernel.async.BackgroundLoop;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Notifies the {@link EventRecorder} to persist events (if enough data is available).
 */
@Register(classes = BackgroundLoop.class)
public class EventProcessorLoop extends BackgroundLoop {

    @Part
    private EventRecorder eventRecorder;

    @Nonnull
    @Override
    public String getName() {
        return "event-processor";
    }

    @Nullable
    @Override
    protected String doWork() throws Exception {
        int processedEvents = eventRecorder.processIfBufferIsFilled();
        return processedEvents == 0 ? null : Strings.apply("Events processed: %s", processedEvents);
    }
}
