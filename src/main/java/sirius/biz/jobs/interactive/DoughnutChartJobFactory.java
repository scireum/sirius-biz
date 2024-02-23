/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.interactive;

/**
 * Provides a base implementation for interactive doughnut charts.
 */
public abstract class DoughnutChartJobFactory extends SingleDatasetChartJobFactory {

    @Override
    public String getIcon() {
        return "fa-solid fa-chart-pie";
    }

    @Override
    protected String getTemplate() {
        return "/templates/biz/jobs/doughnutchart.html.pasta";
    }
}
