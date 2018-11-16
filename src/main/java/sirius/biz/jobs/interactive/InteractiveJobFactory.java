/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.interactive;

import io.netty.handler.codec.http.HttpResponseStatus;
import sirius.biz.analytics.reports.Cells;
import sirius.biz.jobs.BasicJobFactory;
import sirius.kernel.async.Tasks;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.web.controller.Message;
import sirius.web.http.WebContext;
import sirius.web.security.UserContext;
import sirius.web.services.JSONStructuredOutput;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Provides a base implementation for interactive jobs.
 * <p>
 * An interactive job is only available within the UI a provides a response or result with at most a negligible delay.
 */
public abstract class InteractiveJobFactory extends BasicJobFactory {

    /**
     * Defines the executor in which interactive jobs are executed. As these are expected to be short running,
     * a small pool will do.
     */
    private static final String INTERACTIVE_JOBS_EXECUTOR = "interactive-jobs";

    @Part
    private Tasks tasks;

    @Part
    protected Cells cells;

    @Override
    public boolean canStartInUI() {
        return true;
    }

    @Override
    public void startInUI(WebContext request) {
        checkPermissions();
        setupTaskContext();

        AtomicBoolean submit = new AtomicBoolean(request.isSafePOST());
        Map<String, String> context = buildAndVerifyContext(request::get, submit.get(), error -> {
            UserContext.message(Message.error(error));
            submit.set(false);
        });

        tasks.executor(INTERACTIVE_JOBS_EXECUTOR).fork(() -> {
            try {
                generateResponse(request, context);
            } catch (Exception e) {
                request.respondWith()
                       .error(HttpResponseStatus.INTERNAL_SERVER_ERROR, Exceptions.handle(Log.BACKGROUND, e));
            }
        });
    }

    /**
     * Executes the job by generating a response to the given request.
     *
     * @param request the request to respond to
     * @param context the context containing the provided parameters
     */
    protected abstract void generateResponse(WebContext request, Map<String, String> context);

    @Override
    protected void executeInUI(WebContext request, Map<String, String> context) {
        throw new UnsupportedOperationException("unreachable");
    }

    @Override
    public boolean canStartInCall() {
        return false;
    }

    @Override
    protected void executeInCall(JSONStructuredOutput out, Map<String, String> context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean canStartInBackground() {
        return false;
    }

    @Override
    protected void executeInBackground(Map<String, String> context) {
        throw new UnsupportedOperationException();
    }
}
