/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.protocol;

import sirius.biz.web.DateRange;
import sirius.biz.web.PageHelper;
import sirius.db.jdbc.OMA;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.nls.NLS;
import sirius.web.controller.BasicController;
import sirius.web.controller.Controller;
import sirius.web.controller.DefaultRoute;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.Permission;

/**
 * Provides a GUI for viewing the system journal recorded by {@link JournalData} / {@link JournalEntry}.
 */
@Register(classes = Controller.class, framework = Protocols.FRAMEWORK_JOURNAL)
public class JournalController extends BasicController {

    @Part
    private OMA oma;

    /**
     * Displays all changes on entities recorded by the protocol.
     *
     * @param ctx the current request
     */
    @Permission(Protocols.PERMISSION_SYSTEM_JOURNAL)
    @DefaultRoute
    @Routed("/system/protocol")
    public void protocol(WebContext ctx) {
        PageHelper<JournalEntry> ph = PageHelper.withQuery(oma.select(JournalEntry.class).orderDesc(JournalEntry.TOD));
        ph.withContext(ctx);
        ph.addQueryFacet(JournalEntry.TARGET_TYPE.getName(),
                         NLS.get("JournalEntry.targetType"),
                         q -> oma.select(JournalEntry.class)
                                 .distinctFields(JournalEntry.TARGET_TYPE, JournalEntry.TARGET_TYPE)
                                 .asSQLQuery());
        ph.addTimeFacet(JournalEntry.TOD.getName(),
                        NLS.get("JournalEntry.tod"),
                        DateRange.lastFiveMinutes(),
                        DateRange.lastFiveteenMinutes(),
                        DateRange.lastTwoHours(),
                        DateRange.today(),
                        DateRange.yesterday(),
                        DateRange.thisWeek(),
                        DateRange.lastWeek());
        ph.withSearchFields(JournalEntry.CHANGES, JournalEntry.TARGET_NAME, JournalEntry.USERNAME);

        ctx.respondWith().template("templates/protocol/protocol.html.pasta", ph.asPage());
    }
}
