/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.interactive;

import sirius.biz.analytics.charts.Charts;
import sirius.biz.analytics.charts.Dataset;
import sirius.web.http.WebContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public abstract class DougnutChartJobFactory extends SingleDatasetChartJobFactory {


    @Override
    protected String getTemplate() {
        return "/templates/jobs/dougnutchart.html.pasta";
    }

}
