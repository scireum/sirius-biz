/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.interactive;

public abstract class PolarAreaChartJobFactory extends SingleDatasetChartJobFactory {

    @Override
    public String getIcon() {
        return "fa-pie-chart";
    }

    @Override
    protected String getTemplate() {
        return "/templates/jobs/polarareachart.html.pasta";
    }
}
