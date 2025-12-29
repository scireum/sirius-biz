/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.util;

import sirius.biz.analytics.events.EventRecorder;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.std.Part;
import sirius.kernel.xml.SOAPClient;
import sirius.kernel.xml.StructuredNode;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.URL;
import java.net.http.HttpClient;

/**
 * Provides a SOAP client which logs all activities as {@link SOAPCallEvent soap call events}.
 * <p>
 * This permits to monitor API usage and performance.
 */
public class MonitoredSOAPClient extends SOAPClient {

    @Part
    protected static EventRecorder eventRecorder;

    /**
     * Creates a new client which talks to the given endpoint.
     * <p>
     * Note that if the actual endpoint depends on the <tt>action</tt> of the call, use
     * {@link #withCustomEndpoint(String, URL)}.
     *
     * @param clientSelector the selector used to cache the underlying {@link HttpClient} to facilitate connection pooling
     *                       See {@link sirius.kernel.commons.Outcall#modifyClient(String)}.
     * @param endpoint       the default endpoint to talk to
     */
    public MonitoredSOAPClient(@Nullable String clientSelector, @Nonnull URL endpoint) {
        super(clientSelector, endpoint);
    }

    @Override
    protected StructuredNode handleSOAPFault(Watch watch,
                                             @Nonnull String action,
                                             URL effectiveEndpoint,
                                             StructuredNode fault) {
        eventRecorder.record(new SOAPCallEvent().withEndpoint(effectiveEndpoint.toString())
                                                .withAction(action)
                                                .withCallDuration(watch.elapsedMillis())
                                                .withFault(fault.queryString(NODE_FAULTCODE),
                                                           fault.queryString(NODE_FAULTSTRING)));

        return super.handleSOAPFault(watch, action, effectiveEndpoint, fault);
    }

    @Override
    protected StructuredNode handleGeneralFault(Watch watch,
                                                String action,
                                                URL effectiveEndpoint,
                                                Exception exception) {
        eventRecorder.record(new SOAPCallEvent().withEndpoint(effectiveEndpoint.toString())
                                                .withAction(action)
                                                .withCallDuration(watch.elapsedMillis())
                                                .withError(Strings.apply("%s (%s)",
                                                                         exception.getMessage(),
                                                                         exception.getClass().getName())));

        return super.handleGeneralFault(watch, action, effectiveEndpoint, exception);
    }

    @Override
    protected StructuredNode handleResult(Watch watch, String action, URL effectiveEndpoint, StructuredNode result) {
        eventRecorder.record(new SOAPCallEvent().withEndpoint(effectiveEndpoint.toString())
                                                .withAction(action)
                                                .withCallDuration(watch.elapsedMillis()));

        return super.handleResult(watch, action, effectiveEndpoint, result);
    }
}
