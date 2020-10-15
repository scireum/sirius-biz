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
import sirius.biz.codelists.CodeListImportJob;
import sirius.biz.importer.ImportContext;
import sirius.biz.jobs.batch.file.EntityImportJob;
import sirius.biz.jobs.batch.file.EntityImportJobFactory;
import sirius.biz.jobs.params.CodeListParameter;
import sirius.biz.jobs.params.LanguageParameter;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.process.ProcessContext;
import sirius.db.mixing.BaseEntity;
import sirius.kernel.di.std.Register;
import sirius.web.http.QueryString;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Provides an import job for {@link SQLCodeList code lists} stored in a JDBC database.
 */
@Register(framework = SQLCodeLists.FRAMEWORK_CODE_LISTS_JDBC)
@Permission(CodeListController.PERMISSION_MANAGE_CODELISTS)
public class SQLCodeListImportJobFactory extends EntityImportJobFactory {
    /**
     * Contains the SQL code list to import the code list entries into.
     */
    private static final CodeListParameter CODE_LIST_PARAMETER =
            new CodeListParameter("codeList", "$CodeList").markRequired();
    private static final LanguageParameter LANGUAGE_PARAMETER =
            new LanguageParameter(LanguageParameter.PARAMETER_NAME, "$LocaleData.lang");

    @Override
    protected EntityImportJob<SQLCodeListEntry> createJob(ProcessContext process) {
        return new CodeListImportJob<>(fileParameter,
                                       ignoreEmptyParameter,
                                       importModeParameter,
                                       CODE_LIST_PARAMETER,
                                       LANGUAGE_PARAMETER,
                                       SQLCodeListEntry.class,
                                       getDictionary(),
                                       process,
                                       this.getClass().getName());
    }

    @Nonnull
    @Override
    public String getName() {
        return "import-sql-code-list-entries";
    }

    @Override
    protected Class<? extends BaseEntity<?>> getImportType() {
        return SQLCodeListEntry.class;
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?, ?>> parameterCollector) {
        parameterCollector.accept(CODE_LIST_PARAMETER);
        parameterCollector.accept(LANGUAGE_PARAMETER);
        super.collectParameters(parameterCollector);
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
}
