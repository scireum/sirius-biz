/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists.jdbc;

import sirius.biz.codelists.CodeList;
import sirius.biz.codelists.CodeListController;
import sirius.biz.jobs.JobFactory;
import sirius.biz.jobs.batch.file.EntityImportJob;
import sirius.biz.jobs.batch.file.EntityImportJobFactory;
import sirius.biz.jobs.params.CodeListParameter;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.process.ProcessContext;
import sirius.biz.tenants.jdbc.SQLTenants;
import sirius.db.mixing.BaseEntity;
import sirius.kernel.commons.Context;
import sirius.kernel.di.std.Register;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Provides an import job for {@link SQLCodeList code lists} stored in a JDBC database.
 */
@Register(classes = JobFactory.class, framework = SQLTenants.FRAMEWORK_TENANTS_JDBC)
@Permission(CodeListController.PERMISSION_MANAGE_CODELISTS)
public class SQLCodeListEntryImportJobFactory extends EntityImportJobFactory {

    /**
     * Contains the sql code list to import the code list entries into.
     */
    private CodeListParameter codeListParameter = new CodeListParameter("codeList", "$CodeList");

    @Override
    protected void collectParameters(Consumer<Parameter<?, ?>> parameterCollector) {
        super.collectParameters(parameterCollector);

        parameterCollector.accept(codeListParameter);
    }

    @Override
    protected EntityImportJob<SQLCodeListEntry> createJob(ProcessContext process) {
        return new SQLCodeListEntryImportJob(process);
    }

    protected class SQLCodeListEntryImportJob extends EntityImportJob<SQLCodeListEntry> {

        private CodeList codeList;

        /**
         * Creates a new job for the given factory, name and process.
         *
         * @param process the process context itself
         */
        private SQLCodeListEntryImportJob(ProcessContext process) {
            super(fileParameter,
                  ignoreEmptyParameter,
                  importModeParameter,
                  SQLCodeListEntry.class,
                  getDictionary(),
                  process);
            codeList = process.require(codeListParameter);
        }

        @Override
        protected SQLCodeListEntry findAndLoad(Context ctx) {
            ctx.put(SQLCodeListEntry.CODE_LIST.toString(), ((SQLCodeList) codeList).getId());
            return super.findAndLoad(ctx);
        }

        @Override
        protected SQLCodeListEntry fillAndVerify(SQLCodeListEntry entity) {
            setOrVerify(entity, entity.getCodeList(), (SQLCodeList) codeList);
            return super.fillAndVerify(entity);
        }
    }

    @Override
    protected Class<? extends BaseEntity<?>> getImportType() {
        return SQLCodeListEntry.class;
    }

    @Nonnull
    @Override
    public String getName() {
        return "import-sql-code-list-entries";
    }

    @Override
    protected boolean hasPresetFor(Object targetObject) {
        return targetObject instanceof SQLCodeList;
    }

    @Override
    protected void computePresetFor(Object targetObject, Map<String, Object> preset) {
        preset.put(codeListParameter.getName(), ((SQLCodeList) targetObject).getId());
    }
}
