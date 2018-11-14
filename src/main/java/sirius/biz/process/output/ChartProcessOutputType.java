/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process.output;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import sirius.biz.analytics.charts.Dataset;
import sirius.biz.process.Process;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.process.logs.ProcessLogType;
import sirius.biz.web.ElasticPageHelper;
import sirius.db.es.Elastic;
import sirius.db.es.ElasticQuery;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.web.http.WebContext;

import javax.annotation.Nonnull;
import java.util.List;

@Register(classes = {ProcessOutputType.class, ChartProcessOutputType.class})
public class ChartProcessOutputType implements ProcessOutputType {

    @Part
    private Elastic elastic;

    @Nonnull
    @Override
    public String getName() {
        return "chart";
    }


    @Override
    public String getIcon() {
        return "fa-bar-chart";
    }

    public ChartOutput addCharts(ProcessContext process, String name, String label) {
        ProcessOutput output = new ProcessOutput().withType(getName()).withName(name).withLabel(label);
        process.addOutput(output);
        return new ChartOutput(name, process);
    }

    public String parseChartData(ProcessLog chart) {
        try {
            JSONObject obj = JSON.parseObject(chart.getMessage());
            return obj.toJSONString();
        } catch (Exception e) {
            Exceptions.ignore(e);
            return "{}";
        }
    }

    @Override
    public void render(WebContext ctx, Process process, ProcessOutput output) {
        ElasticQuery<ProcessLog> query = elastic.select(ProcessLog.class)
                                                .eq(ProcessLog.OUTPUT, output.getName())
                                                .eq(ProcessLog.PROCESS, process)
                                                .orderAsc(ProcessLog.SORT_KEY);

        ElasticPageHelper<ProcessLog> ph = ElasticPageHelper.withQuery(query);
        ph.withContext(ctx);
        ctx.respondWith()
           .template("/templates/process/process-output-chart.html.pasta",
                     this,
                     process,
                     ph.asPage(),
                     output.getName());
    }
}
