/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process.output;

import sirius.biz.analytics.reports.Cells;
import sirius.biz.process.Process;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.web.ElasticPageHelper;
import sirius.db.es.Elastic;
import sirius.db.es.ElasticQuery;
import sirius.db.mixing.DateRange;
import sirius.db.mixing.query.QueryField;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.nls.NLS;
import sirius.web.http.WebContext;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.List;

/**
 * Represents an output type which renders all log entries as a table.
 * as message.
 * <p>
 * Use {@link ProcessContext#addTable(String, String, List)} and {@link TableOutput} to generate appropriate log entries.
 */
@Register(classes = {ProcessOutputType.class, TableProcessOutputType.class})
public class TableProcessOutputType implements ProcessOutputType {

    /**
     * Contains the type name of this output type.
     */
    public static final String TYPE = "table";

    /**
     * Contains the pipe separated list of column names stored in the {@link ProcessOutput#getContext() context}.
     */
    public static final String CONTEXT_KEY_COLUMNS = "_columns";

    @Part
    private Elastic elastic;

    @Part
    private Cells cells;

    @Nonnull
    @Override
    public String getName() {
        return TYPE;
    }

    @Override
    public String getIcon() {
        return "fa fa-table";
    }

    /**
     * Determines which columns exist in the given output.
     *
     * @param output the output to determine the column from
     * @return the list of columns in the given output
     */
    public List<String> determineColumns(ProcessOutput output) {
        return Arrays.asList(output.getContext().get(CONTEXT_KEY_COLUMNS).orElse("").split("\\|"));
    }

    /**
     * Determines the list of column labels based on the given output and column names.
     *
     * @param output  the output to determine the labels for
     * @param columns the list of (technical) column names
     * @return the list of visible column labels
     */
    public List<String> determineLabels(ProcessOutput output, List<String> columns) {
        return columns.stream().map(col -> output.getContext().get(col).orElse(col)).map(NLS::smartGet).toList();
    }

    @Override
    public void render(WebContext ctx, Process process, ProcessOutput output) {
        ElasticQuery<ProcessLog> query = elastic.select(ProcessLog.class)
                                                .eq(ProcessLog.OUTPUT, output.getName())
                                                .eq(ProcessLog.PROCESS, process)
                                                .orderAsc(ProcessLog.SORT_KEY);

        ElasticPageHelper<ProcessLog> ph = ElasticPageHelper.withQuery(query);
        ph.withContext(ctx);
        ph.addTimeAggregation(ProcessLog.TIMESTAMP,
                              false,
                              DateRange.LAST_FIVE_MINUTES,
                              DateRange.LAST_FIFTEEN_MINUTES,
                              DateRange.LAST_TWO_HOURS);
        ph.addTermAggregation(ProcessLog.NODE);
        ph.addTermAggregation(ProcessLog.MESSAGE_TYPE, NLS::smartGet);
        ph.withSearchFields(QueryField.contains(ProcessLog.SEARCH_FIELD));
        ph.withTotalCount();
        List<String> columns = determineColumns(output);

        ctx.respondWith()
           .template("/templates/biz/process/process-output-table.html.pasta",
                     cells,
                     process,
                     ph.asPage(),
                     output.getName(),
                     columns,
                     determineLabels(output, columns));
    }
}
