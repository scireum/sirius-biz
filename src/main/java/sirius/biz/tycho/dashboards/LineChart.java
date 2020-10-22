/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.dashboards;

import sirius.biz.analytics.charts.Dataset;
import sirius.web.services.JSONStructuredOutput;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LineChart implements Chart {

    private String label;
    private List<String> labels = Collections.emptyList();
    private List<Dataset> datasets = new ArrayList<>();

    public LineChart(String label) {
        this.label = label;
    }

    public LineChart withLabels(List<String> labels) {
        this.labels = new ArrayList<>(labels);
        return this;
    }

    public LineChart addDataset(Dataset dataset) {
        this.datasets.add(dataset);
        return this;
    }

    @Override
    public void writeJSON(JSONStructuredOutput output) {
        output.beginObject("metric");
        output.property("type", "LineChart");
        output.property("label", label);
        output.array("labels", "label", labels);
        output.beginArray("datasets");
        for (Dataset dataset : datasets) {
            output.beginObject("dataset");
            output.property("label", dataset.getLabel());
            output.property("axis", dataset.getAxis());
            output.array("data", "data", dataset.getValues());
            output.endObject();
        }
        output.endArray();
        output.endObject();
    }
}
