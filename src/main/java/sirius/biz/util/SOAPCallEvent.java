/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.util;

import sirius.biz.analytics.events.Event;
import sirius.biz.analytics.events.UserData;
import sirius.biz.analytics.events.UserEvent;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.annotations.Trim;

/**
 * Record a SOAP call performed by {@link sirius.biz.util.MonitoredSOAPClient}.
 */
public class SOAPCallEvent extends Event<SOAPCallEvent> implements UserEvent {

    /**
     * Contains the shop, customer and user which triggered the event.
     */
    private final UserData userData = new UserData();

    /**
     * Contains the endpoint which has been addressed
     */
    public static final Mapping ENDPOINT = Mapping.named("endpoint");
    private String endpoint;

    /**
     * Contains the name of the called SOAP action.
     */
    public static final Mapping ACTION = Mapping.named("action");
    private String action;

    /**
     * Contains the duration of the call in milliseconds.
     * <p>
     * This includes opening/closing the connection.
     */
    public static final Mapping CALL_DURATION = Mapping.named("callDuration");
    private long callDuration;

    /**
     * Signals if the corresponding soap request was unsuccessful.
     * <p>
     * This is <tt>true</tt> if the soap response had a fault element or no response was returned at all,
     * <tt>false</tt> otherwise.
     */
    public static final Mapping ERRONEOUS = Mapping.named("erroneous");
    private boolean erroneous = false;

    /**
     * Contains the fault code (if the corresponding soap response contained a fault element).
     */
    public static final Mapping FAULT_CODE = Mapping.named("faultCode");
    @Trim
    @NullAllowed
    private String faultCode;

    /**
     * Contains the fault message (if the corresponding soap response contained a fault element)
     * or the root cause message (if there was an exception whilse sending/receiving the request/response).
     */
    public static final Mapping ERROR_MESSAGE = Mapping.named("errorMessage");
    @Trim
    @NullAllowed
    private String errorMessage;

    /**
     * Specifies the endpoint which has been contacted.
     *
     * @param endpoint the endpoint of the call
     * @return the event itself for fluent method calls
     */
    public SOAPCallEvent withEndpoint(String endpoint) {
        this.endpoint = endpoint;
        return this;
    }

    /**
     * Specifies the action which has been invoked.
     *
     * @param action the name of the action being invoked
     * @return the event itself for fluent method calls
     */
    public SOAPCallEvent withAction(String action) {
        this.action = action;
        return this;
    }

    /**
     * Records the call duration of the invocation.
     *
     * @param callDuration the duration of the call
     * @return the event itself for fluent method calls
     */
    public SOAPCallEvent withCallDuration(long callDuration) {
        this.callDuration = callDuration;
        return this;
    }

    /**
     * Records a SOAP fault which was received.
     *
     * @param code    the SOAP fault code sent by the server
     * @param message zhe SOAP fault message sent by the server
     * @return the event itself for fluent method calls
     */
    public SOAPCallEvent withFault(String code, String message) {
        this.faultCode = code;
        this.errorMessage = message;
        this.erroneous = true;

        return this;
    }

    /**
     * Records an external error (IO, network etc.) which occurred.
     *
     * @param message the message to record
     * @return the event itself for fluent method calls
     */
    public SOAPCallEvent withError(String message) {
        this.errorMessage = message;
        this.erroneous = true;

        return this;
    }

    public String getFaultCode() {
        return faultCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getAction() {
        return action;
    }

    public long getCallDuration() {
        return callDuration;
    }

    public boolean isErroneous() {
        return erroneous;
    }

    @Override
    public UserData getUserData() {
        return userData;
    }
}
