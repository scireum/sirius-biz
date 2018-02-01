/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.protocol;

import sirius.biz.web.BizController;
import sirius.biz.web.DateRange;
import sirius.biz.web.PageHelper;
import sirius.kernel.di.std.Register;
import sirius.kernel.nls.NLS;
import sirius.web.controller.Controller;
import sirius.web.controller.DefaultRoute;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.Permission;

/**
 * Provides a GUI for viewing system logs.
 */
@Register(classes = Controller.class, framework = Protocols.FRAMEWORK_PROTOCOLS)
public class MailsController extends BizController {

    /**
     * Lists all recorded mail entries.
     *
     * @param ctx the current request
     */
    @Permission(Protocols.PERMISSION_SYSTEM_PROTOCOLS)
    @DefaultRoute
    @Routed("/system/mails")
    public void mails(final WebContext ctx) {
        PageHelper<MailLogEntry> ph = PageHelper.withQuery(oma.select(MailLogEntry.class).orderDesc(MailLogEntry.TOD));
        ph.withContext(ctx);
        ph.addTimeFacet(MailLogEntry.TOD.getName(),
                        NLS.get("MailLogEntry.tod"),
                        DateRange.lastFiveMinutes(),
                        DateRange.lastFiveteenMinutes(),
                        DateRange.lastTwoHours(),
                        DateRange.today(),
                        DateRange.yesterday(),
                        DateRange.thisWeek(),
                        DateRange.lastWeek());
        ph.withSearchFields(MailLogEntry.SUBJECT,
                            MailLogEntry.SENDER,
                            MailLogEntry.SENDER_NAME,
                            MailLogEntry.RECEIVER,
                            MailLogEntry.RECEIVER_NAME);

        ctx.respondWith().template("templates/protocol/mails.html.pasta", ph.asPage());
    }

    /**
     * Shows the details of a selected mail.
     *
     * @param ctx the current request
     * @param id  the id of the mail to show
     */
    @Permission(Protocols.PERMISSION_SYSTEM_PROTOCOLS)
    @Routed("/system/mail/:1")
    public void mail(final WebContext ctx, String id) {
        MailLogEntry mailLogEntry = find(MailLogEntry.class, id);
        ctx.respondWith().template("templates/protocol/mail.html.pasta", mailLogEntry);
    }
}
