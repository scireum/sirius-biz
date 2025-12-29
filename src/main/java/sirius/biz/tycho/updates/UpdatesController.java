/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.updates;

import sirius.biz.analytics.events.EventRecorder;
import sirius.biz.web.BizController;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Urls;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.services.InternalService;
import sirius.web.services.JSONStructuredOutput;

/**
 * Provides a minimal API which creates a {@link UpdateClickEvent} if a user clicks on the "read more" link for an
 * {@link UpdateInfo update}.
 */
@Register
public class UpdatesController extends BizController {

    @Part
    private EventRecorder eventRecorder;

    /**
     * Records a click event for the given update and current user (if present).
     *
     * @param webContext the request to respond to
     * @param output     the JSON response - which is empty in this case, as this is a fire and forget operation for the
     *                   client
     */
    @InternalService
    @Routed("/tycho/updates/markAsSeen")
    public void markUpdatesAsSeen(WebContext webContext, JSONStructuredOutput output) {
        String updateId = webContext.require("updateId").asString();
        if (Urls.isHttpUrl(updateId)) {
            eventRecorder.record(new UpdateClickEvent().forUpdateGuid(updateId));
        } else {
            throw Exceptions.createHandled()
                            .withDirectMessage(Strings.apply("Invalid updateId: %s", updateId))
                            .handle();
        }
    }
}
