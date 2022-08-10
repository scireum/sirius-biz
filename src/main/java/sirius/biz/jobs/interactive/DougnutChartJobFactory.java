/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.interactive;

/**
 * Provides a base implementation for interactive dougnut charts.
 */
public abstract class DougnutChartJobFactory extends SingleDatasetChartJobFactory {

    @Override
    public String getIcon() {
        return "fa fa-pie-chart";
    }

    @Override
    protected String getTemplate() {
        return "/templates/biz/jobs/dougnutchart.html.pasta";
    }
}
