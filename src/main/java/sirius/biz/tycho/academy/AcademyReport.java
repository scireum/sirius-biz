/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.academy;

import sirius.biz.analytics.reports.Cell;
import sirius.biz.analytics.reports.Report;
import sirius.biz.jobs.StandardCategories;
import sirius.biz.jobs.interactive.ReportJobFactory;
import sirius.biz.jobs.params.Parameter;
import sirius.kernel.nls.NLS;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Provides the base class to report the usage of the onboarding / video academy.
 */
public abstract class AcademyReport extends ReportJobFactory {

    protected static final String COLUMN_TRACK = "track";
    protected static final String COLUMN_VIDEO = "video";
    protected static final String COLUMN_WATCHED = "watched";
    protected static final String COLUMN_SKIPPED = "skipped";
    protected static final String COLUMN_RECOMMENDED = "recommended";
    protected static final String COLUMN_PERCENT_WATCHED = "percentWatched";

    @Override
    protected void collectParameters(Consumer<Parameter<?>> parameterCollector) {
        // This job has no parameters...
    }

    @Override
    public String getCategory() {
        return StandardCategories.MONITORING;
    }

    @Override
    public int getPriority() {
        return 8400;
    }

    @Override
    public String getLabel() {
        return NLS.get("AcademyReport.label");
    }

    @Override
    public String getDescription() {
        return NLS.get("AcademyReport.description");
    }

    @Override
    protected void computeReport(Map<String, String> context,
                                 Report report,
                                 BiConsumer<String, Cell> additionalMetricConsumer) throws Exception {
        report.addColumn(COLUMN_TRACK, "$AcademyReport.track");
        report.addColumn(COLUMN_VIDEO, "$AcademyReport.video");
        report.addColumn(COLUMN_WATCHED, "$AcademyReport.watched");
        report.addColumn(COLUMN_SKIPPED, "$AcademyReport.skipped");
        report.addColumn(COLUMN_RECOMMENDED, "$AcademyReport.recommended");
        report.addColumn(COLUMN_PERCENT_WATCHED, "$AcademyReport.percentWatched");

        outputVideos(report);
    }

    protected abstract void outputVideos(Report report);
}
