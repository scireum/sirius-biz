/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists.jdbc;

import sirius.biz.codelists.CodeListController;
import sirius.biz.codelists.CodeListEntry;
import sirius.biz.codelists.CodeListExportJob;
import sirius.biz.importer.ImportContext;
import sirius.biz.jobs.batch.file.EntityExportJob;
import sirius.biz.jobs.batch.file.EntityExportJobFactory;
import sirius.biz.jobs.params.CodeListParameter;
import sirius.biz.jobs.params.LanguageParameter;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.process.ProcessContext;
import sirius.db.jdbc.SmartQuery;
import sirius.kernel.commons.Explain;
import sirius.kernel.di.std.Register;
import sirius.web.http.QueryString;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Provides an export for entries of a {@link sirius.biz.codelists.jdbc.SQLCodeList}.
 */
@Register(framework = SQLCodeLists.FRAMEWORK_CODE_LISTS_JDBC)
@Permission(CodeListController.PERMISSION_MANAGE_CODELISTS)
public class SQLCodeListExportJobFactory
        extends EntityExportJobFactory<SQLCodeListEntry, SmartQuery<SQLCodeListEntry>> {

    private static final CodeListParameter CODE_LIST_PARAMETER =
            new CodeListParameter("codeList", "$CodeList").markRequired();
    private static final LanguageParameter LANGUAGE_PARAMETER = (LanguageParameter) new LanguageParameter(
            LanguageParameter.PARAMETER_NAME,
            "$LocaleData.lang").withDescription("$CodeList.export.lang.help");

    @Nonnull
    @Override
    public String getName() {
        return "export-sql-code-list-entries";
    }

    @Override
    protected Class<SQLCodeListEntry> getExportType() {
        return SQLCodeListEntry.class;
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?, ?>> parameterCollector) {
        parameterCollector.accept(CODE_LIST_PARAMETER);
        parameterCollector.accept(LANGUAGE_PARAMETER);
        super.collectParameters(parameterCollector);
    }

    @Override
    protected void extendSelectQuery(SmartQuery<SQLCodeListEntry> query, ProcessContext processContext) {
        query.eq(CodeListEntry.CODE_LIST, processContext.require(CODE_LIST_PARAMETER));
    }

    @Override
    protected void transferParameters(ImportContext context, ProcessContext processContext) {
        context.set(CodeListEntry.CODE_LIST, processContext.require(CODE_LIST_PARAMETER));
        context.set(LANGUAGE_PARAMETER.getName(), processContext.getParameter(LANGUAGE_PARAMETER));
    }

    @Override
    protected boolean hasPresetFor(QueryString queryString, Object targetObject) {
        return targetObject instanceof SQLCodeList;
    }

    @Override
    protected void computePresetFor(QueryString queryString, Object targetObject, Map<String, Object> preset) {
        preset.put(CODE_LIST_PARAMETER.getName(), ((SQLCodeList) targetObject).getCodeListData().getCode());
        queryString.get(LANGUAGE_PARAMETER.getName())
                   .ifFilled(value -> preset.put(LANGUAGE_PARAMETER.getName(), value));
    }

    @SuppressWarnings("squid:S2095")
    @Explain("The job must not be closed here as it is returned and managed by the caller.")
    @Override
    protected EntityExportJob<SQLCodeListEntry, SmartQuery<SQLCodeListEntry>> createJob(ProcessContext process) {
        ImportContext parameterContext = new ImportContext();
        transferParameters(parameterContext, process);

        return new CodeListExportJob<SQLCodeListEntry, SmartQuery<SQLCodeListEntry>>(templateFileParameter,
                                                                                     destinationParameter,
                                                                                     fileTypeParameter,
                                                                                     getExportType(),
                                                                                     getDictionary(),
                                                                                     getDefaultMapping(),
                                                                                     process,
                                                                                     getName(),
                                                                                     process.getParameter(
                                                                                             LANGUAGE_PARAMETER),
                                                                                     process.require(CODE_LIST_PARAMETER))
                .withQueryExtender(query -> extendSelectQuery(query, process))
                .withContextExtender(context -> context.putAll(parameterContext))
                .withFileName(getCustomFileName());
    }
}
