/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process.output;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import sirius.biz.analytics.metrics.Dataset;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.process.logs.ProcessLogType;
import sirius.kernel.commons.Json;

import java.util.List;

/**
 * Used to store one or more charts for a {@link ProcessOutput} of type {@link ChartProcessOutputType}.
 * <p>
 * To create an appropriate output, {@link ProcessContext#addCharts(String, String)} can be used.
 */
public class ChartOutput {

    private static final String KEY_TYPE = "type";
    private static final String KEY_LABELS = "labels";
    private static final String KEY_LINES = "lines";
    private static final String KEY_LABEL = "label";
    private static final String KEY_DATA = "data";
    private static final String TYPE_LINE = "line";
    private static final String TYPE_BAR = "bar";
    private static final String KEY_BARS = "bars";
    private static final String TYPE_POLAR_AREA = "polarArea";
    private static final String TYPE_DOUGNUT = "dougnut";

    private final String name;
    private final ProcessContext process;

    /**
     * Creates a new chart output for the given output name and process.
     *
     * @param name    the name of the {@link ProcessOutput} to fill
     * @param process the context representing a {@link Process} used to store the charts
     */
    public ChartOutput(String name, ProcessContext process) {
        this.name = name;
        this.process = process;
    }

    /**
     * Adds a line chart.
     *
     * @param labels   the labels used on the X axis
     * @param datasets the datasets representing the lines to draw
     */
    public void addLineChart(List<String> labels, List<Dataset> datasets) {
        ObjectNode chart = Json.createObject();
        chart.put(KEY_TYPE, TYPE_LINE);
        chart.putPOJO(KEY_LABELS, labels);
        ArrayNode lines = Json.createArray();
        chart.set(KEY_LINES, lines);
        for (Dataset dataset : datasets) {
            ObjectNode data = Json.createObject();
            data.put(KEY_LABEL, dataset.getLabel());
            data.putPOJO(KEY_DATA, dataset.getValues());
            lines.add(data);
        }

        process.log(new ProcessLog().withType(ProcessLogType.INFO).into(name).withMessage(Json.write(chart)));
    }

    /**
     * Adds a bar chart.
     *
     * @param labels   the labels used on the X axis
     * @param datasets the datasets representing the bars to draw
     */
    public void addBarChart(List<String> labels, List<Dataset> datasets) {
        ObjectNode chart = Json.createObject();
        chart.put(KEY_TYPE, TYPE_BAR);
        chart.putPOJO(KEY_LABELS, labels);
        ArrayNode bars = Json.createArray();
        chart.set(KEY_BARS, bars);
        for (Dataset dataset : datasets) {
            ObjectNode data = Json.createObject();
            data.put(KEY_LABEL, dataset.getLabel());
            data.putPOJO(KEY_DATA, dataset.getValues());
            bars.add(data);
        }

        process.log(new ProcessLog().withType(ProcessLogType.INFO).into(name).withMessage(Json.write(chart)));
    }

    /**
     * Adds a polar area chart.
     *
     * @param labels  the labels used for each area
     * @param dataset the dataset representing the areas to draw
     */
    public void addPolarAreaChart(List<String> labels, Dataset dataset) {
        ObjectNode chart = Json.createObject();
        chart.put(KEY_TYPE, TYPE_POLAR_AREA);
        chart.putPOJO(KEY_LABELS, labels);
        chart.putPOJO(KEY_DATA, dataset.getValues());

        process.log(new ProcessLog().withType(ProcessLogType.INFO).into(name).withMessage(Json.write(chart)));
    }

    /**
     * Adds a dougnut chart.
     *
     * @param labels  the labels used for each slice
     * @param dataset the dataset representing the size of each slice
     */
    public void addDougnutChart(List<String> labels, Dataset dataset) {
        ObjectNode chart = Json.createObject();
        chart.put(KEY_TYPE, TYPE_DOUGNUT);
        chart.putPOJO(KEY_LABELS, labels);
        chart.putPOJO(KEY_DATA, dataset.getValues());

        process.log(new ProcessLog().withType(ProcessLogType.INFO).into(name).withMessage(Json.write(chart)));
    }
}
