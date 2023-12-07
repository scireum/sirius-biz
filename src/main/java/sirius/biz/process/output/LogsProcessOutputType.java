/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process.output;

import sirius.biz.process.Process;
import sirius.biz.process.ProcessController;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.process.logs.ProcessLogState;
import sirius.biz.process.logs.ProcessLogType;
import sirius.biz.web.ElasticPageHelper;
import sirius.db.es.Elastic;
import sirius.db.es.ElasticQuery;
import sirius.db.mixing.DateRange;
import sirius.db.mixing.query.QueryField;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.nls.NLS;
import sirius.web.http.WebContext;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import javax.annotation.Nonnull;

/**
 * Represents an output type which renders the log entries as additional list of recorded log messages.
 * <p>
 * Use {@link sirius.biz.process.ProcessContext#addLogOutput(String, String)} to create an appropriate output.
 */
@Register(classes = {ProcessOutputType.class, LogsProcessOutputType.class})
public class LogsProcessOutputType implements ProcessOutputType {

    /**
     * Contains the type name of this output type.
     */
    public static final String TYPE = "logs";

    @Part
    private Elastic elastic;

    @Nonnull
    @Override
    public String getName() {
        return TYPE;
    }

    @Override
    public String getIcon() {
        return "fa fa-bars";
    }

    @Override
    public void render(WebContext webContext, Process process, ProcessOutput output) {
        ElasticQuery<ProcessLog> query = elastic.select(ProcessLog.class)
                                                .eq(ProcessLog.OUTPUT, output.getName())
                                                .eq(ProcessLog.PROCESS, process)
                                                .orderDesc(ProcessLog.SORT_KEY);

        UserInfo user = UserContext.getCurrentUser();
        if (!user.hasPermission(ProcessController.PERMISSION_MANAGE_ALL_PROCESSES)) {
            query.eq(ProcessLog.SYSTEM_MESSAGE, false);
        }

        ElasticPageHelper<ProcessLog> pageHelper = ElasticPageHelper.withQuery(query);
        pageHelper.withContext(webContext)
                  .addTermAggregation(ProcessLog.TYPE, ProcessLogType.class)
                  .addTermAggregation(ProcessLog.STATE, ProcessLogState.class)
                  .addTermAggregation(ProcessLog.MESSAGE_TYPE, NLS::smartGet);
        if (user.hasPermission(ProcessController.PERMISSION_MANAGE_ALL_PROCESSES)) {
            pageHelper.addBooleanAggregation(ProcessLog.SYSTEM_MESSAGE);
        }
        pageHelper.addTimeAggregation(ProcessLog.TIMESTAMP,
                                      false,
                                      DateRange.LAST_FIVE_MINUTES,
                                      DateRange.LAST_FIFTEEN_MINUTES,
                                      DateRange.LAST_TWO_HOURS)
                  .addTermAggregation(ProcessLog.NODE)
                  .withSearchFields(QueryField.contains(ProcessLog.SEARCH_FIELD));

        webContext.respondWith()
                  .template("/templates/biz/process/process-output-logs.html.pasta",
                            process,
                            pageHelper.asPage(),
                            output.getName());
    }
}
