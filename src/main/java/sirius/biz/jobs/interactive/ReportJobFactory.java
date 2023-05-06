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
import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Tuple;
import sirius.kernel.nls.NLS;
import sirius.web.http.WebContext;
import sirius.web.security.UserContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Provides a base implementation for interactive charts which compute and display a {@link Report}.
 */
public abstract class ReportJobFactory extends InteractiveJobFactory {

    @Override
    public String getIcon() {
        return "fas fa-table";
    }

    @Override
    protected void generateResponse(WebContext request, Map<String, String> context) {
        Report report = new Report();
        List<Tuple<String, Cell>> additionalMetrics = new ArrayList<>();

        try {
            computeReport(context,
                          report,
                          (name, value) -> additionalMetrics.add(Tuple.create(NLS.smartGet(name), value)));
        } catch (Exception e) {
            UserContext.handle(e);
        }

        request.respondWith().template(getTemplate(), this, context, report, additionalMetrics);
    }

    /**
     * Specifies the template used to render the report.
     *
     * @return the name or path of the template to render
     */
    @SuppressWarnings("squid:S3400")
    @Explain("Maybe a subclass might want to supply a custom template")
    protected String getTemplate() {
        return "/templates/biz/jobs/report.html.pasta";
    }

    /**
     * Populates the given report with data.
     *
     * @param context                  the parameters provided by the user
     * @param report                   the report to populate
     * @param additionalMetricConsumer used to collect additional metrics (label and the value represented as cell).
     *                                 This can be used to output averages, min and max values etc. Note that the
     *                                 name will be {@link NLS#smartGet(String) auto-translated}.
     * @throws Exception in case of an unexpected error which aborted the computations
     */
    protected abstract void computeReport(Map<String, String> context,
                                          Report report,
                                          BiConsumer<String, Cell> additionalMetricConsumer) throws Exception;
}
