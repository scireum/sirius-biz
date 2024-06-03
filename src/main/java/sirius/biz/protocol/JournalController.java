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
import sirius.web.controller.DefaultRoute;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.Permission;

/**
 * Provides a GUI for viewing the system journal recorded by {@link JournalData} / {@link JournalEntry}.
 */
@Register(framework = Protocols.FRAMEWORK_JOURNAL)
public class JournalController extends BizController {

    /**
     * Displays all changes on entries recorded by the protocol.
     *
     * @param webContext the current request
     */
    @Permission(Protocols.PERMISSION_SYSTEM_JOURNAL)
    @DefaultRoute
    @Routed("/system/protocol")
    public void protocol(WebContext webContext) {
        ElasticPageHelper<JournalEntry> pageHelper =
                ElasticPageHelper.withQuery(elastic.select(JournalEntry.class).orderDesc(JournalEntry.TOD));
        pageHelper.withContext(webContext);
        pageHelper.addTermAggregation(JournalEntry.TARGET_TYPE);
        pageHelper.addTimeAggregation(JournalEntry.TOD,
                                      false,
                                      DateRange.LAST_FIVE_MINUTES,
                                      DateRange.LAST_FIFTEEN_MINUTES,
                                      DateRange.LAST_TWO_HOURS,
                                      DateRange.TODAY,
                                      DateRange.YESTERDAY,
                                      DateRange.THIS_WEEK,
                                      DateRange.LAST_WEEK);
        pageHelper.withSearchFields(QueryField.contains(JournalEntry.SEARCH_FIELD));
        pageHelper.withTotalCount();
        webContext.respondWith().template("/templates/biz/protocol/protocol.html.pasta", pageHelper.asPage());
    }

    /**
     * Displays all changes on entries recorded for a given entity by the protocol.
     *
     * @param webContext the current request
     * @param type       the type of the object to report the journal for
     * @param id         the id of the object to report the journal for
     */
    @Routed("/system/protocol/:1/:2")
    public void entityProtocol(WebContext webContext, String type, String id) {
        if (!verifySignedLink(webContext)) {
            return;
        }

        ElasticPageHelper<JournalEntry> pageHelper = ElasticPageHelper.withQuery(elastic.select(JournalEntry.class)
                                                                                        .eq(JournalEntry.TARGET_TYPE,
                                                                                            type)
                                                                                        .eq(JournalEntry.TARGET_ID, id)
                                                                                        .orderDesc(JournalEntry.TOD));
        pageHelper.withContext(webContext)
                  .addTimeAggregation(JournalEntry.TOD,
                                      false,
                                      DateRange.LAST_FIVE_MINUTES,
                                      DateRange.LAST_FIFTEEN_MINUTES,
                                      DateRange.LAST_TWO_HOURS,
                                      DateRange.TODAY,
                                      DateRange.YESTERDAY,
                                      DateRange.THIS_WEEK,
                                      DateRange.LAST_WEEK)
                  .withSearchFields(QueryField.contains(JournalEntry.SEARCH_FIELD))
                  .withTotalCount();

        webContext.respondWith()
                  .template("/templates/biz/protocol/entity_protocol.html.pasta", type, id, pageHelper.asPage());
    }
}
