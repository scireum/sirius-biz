/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.reports;

public class PdfReportBuilder {

    public PdfReportBuilder writePageHeading(String heading) {
        return this;
    }

    public PdfReportBuilder writePageSubHeading(String heading) {
        return this;
    }

    public PdfReportBuilder writeTextSection(String textSection) {
        return this;
    }

    public PdfReportBuilder writeHeading(String heading) {
        return this;
    }

    public PdfReportBuilder nextPage() {
        return this;
    }

    public PdfReportBuilder addReportTable(Report report) {
        return this;
    }

}
