/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.interactive;

import sirius.biz.analytics.reports.Cell;
import sirius.biz.analytics.reports.Report;
import sirius.kernel.commons.Tuple;
import sirius.web.http.WebContext;
import sirius.web.security.UserContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public abstract class ReportJobFactory extends InteractiveJobFactory {

    @Override
    public String getIcon() {
        return "fa-file-image-o";
    }

    @Override
    protected void generateResponse(WebContext request, Map<String, String> context) {
        Report report = new Report();
        List<Tuple<String, Cell>> additionalMetrics = new ArrayList<>();

        try {
            computeReport(report, (name, value) -> additionalMetrics.add(Tuple.create(name, value)));
        } catch (Exception e) {
            UserContext.handle(e);
        }

        request.respondWith().template(getTemplate(), this, context, report, additionalMetrics);
    }

    protected String getTemplate() {
        return "/templates/jobs/report.html.pasta";
    }

    protected abstract void computeReport(Report report, BiConsumer<String, Cell> additionalMetricConsumer);
}
