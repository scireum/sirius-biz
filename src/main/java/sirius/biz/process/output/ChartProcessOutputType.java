/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process.output;

import com.fasterxml.jackson.databind.node.ObjectNode;
import sirius.biz.process.Process;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.web.ElasticPageHelper;
import sirius.db.es.Elastic;
import sirius.db.es.ElasticQuery;
import sirius.kernel.commons.Json;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.web.http.WebContext;

import javax.annotation.Nonnull;

/**
 * Represents an output type which renders each log entry as a chart by expecting them to have a proper JSON object
 * as message.
 * <p>
 * Use {@link ProcessContext#addCharts(String, String)} and {@link ChartOutput} to generate appropriate log entries.
 */
@Register(classes = {ProcessOutputType.class, ChartProcessOutputType.class})
public class ChartProcessOutputType implements ProcessOutputType {

    /**
     * Contains the type name of this output type.
     */
    public static final String TYPE = "chart";

    @Part
    private Elastic elastic;

    @Nonnull
    @Override
    public String getName() {
        return TYPE;
    }

    @Override
    public String getIcon() {
        return "fa-solid fa-chart-line";
    }

    /**
     * Directly invoked by the template and used to extract the JSON object from a given log entry.
     *
     * @param chart the log entry representing a chart
     * @return the internally stored JSON object encoded as string
     */
    public String parseChartData(ProcessLog chart) {
        try {
            // We decode and re-encode here to ensure that only proper data is output into the HTML page
            ObjectNode obj = Json.parseObject(chart.getMessage());
            return Json.write(obj);
        } catch (Exception e) {
            Exceptions.ignore(e);
            return "{}";
        }
    }

    @Override
    public void render(WebContext webContext, Process process, ProcessOutput output) {
        ElasticQuery<ProcessLog> query = elastic.select(ProcessLog.class)
                                                .eq(ProcessLog.OUTPUT, output.getName())
                                                .eq(ProcessLog.PROCESS, process)
                                                .orderAsc(ProcessLog.SORT_KEY);

        ElasticPageHelper<ProcessLog> pageHelper = ElasticPageHelper.withQuery(query);
        pageHelper.withContext(webContext);
        webContext.respondWith()
                  .template("/templates/biz/process/process-output-chart.html.pasta",
                            this,
                            process,
                            pageHelper.asPage(),
                            output.getName());
    }
}
