/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.infos;

import sirius.biz.analytics.reports.Cells;
import sirius.biz.analytics.reports.Report;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.nls.NLS;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Provides a collector which can be used to populate the documentation of a {@link sirius.biz.jobs.JobFactory job}.
 * <p>
 * This will most probably used in a custom implementation of
 * {@link sirius.biz.jobs.BasicJobFactory#collectJobInfos(JobInfoCollector)}.
 */
public class JobInfoCollector {

    @Part
    private static Cells cells;

    private final List<JobInfo> infos = new ArrayList<>();

    /**
     * Adds a custom info.
     *
     * @param info the info to add
     * @return the collector itself for fluent method calls
     */
    public JobInfoCollector addInfo(JobInfo info) {
        infos.add(info);
        return this;
    }

    /**
     * Adds a {@link HeadingInfo heading info}.
     *
     * @param heading the heading text to add
     * @return the collector itself for fluent method calls
     */
    public JobInfoCollector addHeading(String heading) {
        return addInfo(new HeadingInfo(heading));
    }

    /**
     * Adds a {@link HeadingInfo heading info} using a translated i18n key.
     *
     * @param i18nKey the key used to lookup the translated text
     * @return the collector itself for fluent method calls
     */
    public JobInfoCollector addTranslatedHeading(String i18nKey) {
        return addHeading(NLS.get(i18nKey));
    }

    /**
     * Adds a {@link TextInfo text info}.
     *
     * @param text the text to add
     * @return the collector itself for fluent method calls
     */
    public JobInfoCollector addText(String text) {
        if (Strings.isEmpty(text)) {
            return this;
        }

        return addInfo(new TextInfo(text));
    }

    /**
     * Adds a {@link TextInfo text info} using a translated i18n key.
     *
     * @param i18nKey the key used to lookup the translated text
     * @return the collector itself for fluent method calls
     */
    public JobInfoCollector addTranslatedText(String i18nKey) {
        return addText(NLS.get(i18nKey));
    }

    /**
     * Adds a {@link CardInfo well info}.
     *
     * @param text the text to add as card
     * @return the collector itself for fluent method calls
     */
    public JobInfoCollector addCard(String text) {
        if (Strings.isEmpty(text)) {
            return this;
        }

        return addInfo(new CardInfo(text));
    }

    /**
     * Adds a {@link CardInfo text info} using a translated i18n key.
     *
     * @param i18nKey the key used to lookup the translated text
     * @return the collector itself for fluent method calls
     */
    public JobInfoCollector addTranslatedCard(String i18nKey) {
        return addCard(NLS.get(i18nKey));
    }

    /**
     * Adds a block of unescaped HTML.
     *
     * @param html the html to add
     * @return the collector itself for fluent method calls
     */
    public JobInfoCollector addHTML(String html) {
        return addInfo(new HTMLInfo(html));
    }

    /**
     * Adds a {@link Report report/table}.
     *
     * @param reportProvider a consumer which is provided with the report and the {@link Cells cells helper}.
     *                       This can be used to populate the report.
     * @return the collector itself for fluent method calls
     */
    public JobInfoCollector addReport(BiConsumer<Report, Cells> reportProvider) {
        ReportInfo info = new ReportInfo();
        reportProvider.accept(info.getReport(), cells);
        return addInfo(info);
    }

    /**
     * Provides the info collected so far.
     *
     * @return the infos which have been collected so far
     */
    public List<JobInfo> getInfos() {
        return Collections.unmodifiableList(infos);
    }
}
