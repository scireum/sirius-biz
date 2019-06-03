/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.protocol;

import io.netty.handler.codec.http.HttpResponseStatus;
import sirius.biz.web.BizController;
import sirius.biz.web.ElasticPageHelper;
import sirius.db.mixing.DateRange;
import sirius.db.mixing.query.QueryField;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Register;
import sirius.web.controller.Controller;
import sirius.web.controller.DefaultRoute;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.Permission;

/**
 * Provides a GUI for viewing the system journal recorded by {@link JournalData} / {@link JournalEntry}.
 */
@Register(classes = Controller.class, framework = Protocols.FRAMEWORK_JOURNAL)
public class JournalController extends BizController {

    /**
     * Displays all changes on entries recorded by the protocol.
     *
     * @param ctx the current request
     */
    @Permission(Protocols.PERMISSION_SYSTEM_JOURNAL)
    @DefaultRoute
    @Routed("/system/protocol")
    public void protocol(WebContext ctx) {
        ElasticPageHelper<JournalEntry> ph =
                ElasticPageHelper.withQuery(elastic.select(JournalEntry.class).orderDesc(JournalEntry.TOD));
        ph.withContext(ctx);
        ph.addTermAggregation(JournalEntry.TARGET_TYPE);
        ph.addTimeAggregation(JournalEntry.TOD,
                              DateRange.lastFiveMinutes(),
                              DateRange.lastFiveteenMinutes(),
                              DateRange.lastTwoHours(),
                              DateRange.today(),
                              DateRange.yesterday(),
                              DateRange.thisWeek(),
                              DateRange.lastWeek());
        ph.withSearchFields(QueryField.contains(JournalEntry.SEARCH_FIELD));

        ctx.respondWith().template("templates/biz/protocol/protocol.html.pasta", ph.asPage());
    }

    /**
     * Displays all changes on entries recorded for a given entity by the protocol.
     *
     * @param ctx  the current request
     * @param type the type of the object to report the journal for
     * @param id   the id of the object to report the journal for
     */
    @Routed("/system/protocol/:1/:2")
    public void entityProtocol(WebContext ctx, String type, String id) {
        if (!verifySignedLink(ctx)) {
            return;
        }

        ElasticPageHelper<JournalEntry> ph = ElasticPageHelper.withQuery(elastic.select(JournalEntry.class)
                                                                                .eq(JournalEntry.TARGET_TYPE, type)
                                                                                .eq(JournalEntry.TARGET_ID, id)
                                                                                .orderDesc(JournalEntry.TOD));
        ph.withContext(ctx);
        ph.addTimeAggregation(JournalEntry.TOD,
                              DateRange.lastFiveMinutes(),
                              DateRange.lastFiveteenMinutes(),
                              DateRange.lastTwoHours(),
                              DateRange.today(),
                              DateRange.yesterday(),
                              DateRange.thisWeek(),
                              DateRange.lastWeek());
        ph.withSearchFields(QueryField.contains(JournalEntry.SEARCH_FIELD));

        ctx.respondWith().template("templates/biz/protocol/entity_protocol.html.pasta", type, id, ph.asPage());
    }
}
