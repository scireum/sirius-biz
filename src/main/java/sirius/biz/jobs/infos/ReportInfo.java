/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.infos;

import sirius.biz.analytics.reports.Report;

/**
 * Renders a table / report.
 */
public class ReportInfo implements JobInfo {

    private final Report report = new Report();

    /**
     * Provides access to the report.
     *
     * @return the report which can be modified and enhanced
     */
    public Report getReport() {
        return report;
    }

    @Override
    public String getTemplateName() {
        return "/templates/biz/jobs/infos/report.html.pasta";
    }
}
