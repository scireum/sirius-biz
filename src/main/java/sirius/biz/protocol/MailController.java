/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.protocol;

import sirius.biz.web.BizController;
import sirius.biz.web.ElasticPageHelper;
import sirius.db.mixing.DateRange;
import sirius.db.mixing.query.QueryField;
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
public class MailController extends BizController {

    /**
     * Lists all recorded mail entries.
     *
     * @param ctx the current request
     */
    @Permission(Protocols.PERMISSION_SYSTEM_PROTOCOLS)
    @DefaultRoute
    @Routed("/system/mails")
    public void mails(final WebContext ctx) {
        ElasticPageHelper<MailProtocol> ph =
                ElasticPageHelper.withQuery(elastic.select(MailProtocol.class).orderDesc(MailProtocol.TOD));
        ph.withContext(ctx);
        ph.addBooleanFacet(MailProtocol.SUCCESS.getName(), NLS.get("MailProtocol.success"));
        ph.addTermAggregation(MailProtocol.NODE);
        ph.addTermAggregation(MailProtocol.TYPE);
        ph.addTimeAggregation(MailProtocol.TOD,
                              DateRange.lastFiveMinutes(),
                              DateRange.lastFiveteenMinutes(),
                              DateRange.lastTwoHours(),
                              DateRange.today(),
                              DateRange.yesterday(),
                              DateRange.thisWeek(),
                              DateRange.lastWeek());
        ph.withSearchFields(QueryField.contains(MailProtocol.SEARCH_FIELD));

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
        MailProtocol mailLogEntry = find(MailProtocol.class, id);
        ctx.respondWith().template("templates/protocol/mail.html.pasta", mailLogEntry);
    }
}
