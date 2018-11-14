/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process.output;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import sirius.biz.analytics.charts.Dataset;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.process.logs.ProcessLogType;

import java.util.List;

public class ChartOutput {
    private String name;
    private ProcessContext process;

    public ChartOutput(String name, ProcessContext process) {
        this.name = name;
        this.process = process;
    }

    public void addLineChart(List<String> labels, List<Dataset> datasets) {
        JSONObject chart = new JSONObject();
        chart.put("type", "line");
        chart.put("labels", labels);
        JSONArray lines = new JSONArray();
        chart.put("lines", lines);
        for (Dataset dataset : datasets) {
            JSONObject data = new JSONObject();
            data.put("label", dataset.getLabel());
            data.put("data", dataset.getValues());
            lines.add(data);
        }

        process.log(new ProcessLog().withType(ProcessLogType.INFO).into(name).withMessage(chart.toJSONString()));
    }

    public void addBarChart(List<String> labels, List<Dataset> datasets) {
        JSONObject chart = new JSONObject();
        chart.put("type", "bar");
        chart.put("labels", labels);
        JSONArray bars = new JSONArray();
        chart.put("bars", bars);
        for (Dataset dataset : datasets) {
            JSONObject data = new JSONObject();
            data.put("label", dataset.getLabel());
            data.put("data", dataset.getValues());
            bars.add(data);
        }

        process.log(new ProcessLog().withType(ProcessLogType.INFO).into(name).withMessage(chart.toJSONString()));
    }

    public void addPolarAreaChart(List<String> labels, Dataset dataset) {
        JSONObject chart = new JSONObject();
        chart.put("type", "polarArea");
        chart.put("labels", labels);
        chart.put("data", dataset.getValues());

        process.log(new ProcessLog().withType(ProcessLogType.INFO).into(name).withMessage(chart.toJSONString()));
    }

    public void addDougnutChart(List<String> labels, Dataset dataset) {
        JSONObject chart = new JSONObject();
        chart.put("type", "dougnut");
        chart.put("labels", labels);
        chart.put("data", dataset.getValues());

        process.log(new ProcessLog().withType(ProcessLogType.INFO).into(name).withMessage(chart.toJSONString()));
    }

}
