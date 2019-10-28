/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists.jdbc;

import sirius.biz.codelists.CodeListController;
import sirius.biz.importer.format.ImportDictionary;
import sirius.biz.jobs.JobFactory;
import sirius.biz.jobs.batch.file.LineBasedImportJob;
import sirius.biz.jobs.batch.file.LineBasedImportJobFactory;
import sirius.biz.jobs.params.BooleanParameter;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.jobs.params.VirtualObjectParameter;
import sirius.biz.process.ProcessContext;
import sirius.biz.tenants.jdbc.SQLTenants;
import sirius.db.mixing.BaseEntity;
import sirius.kernel.commons.Context;
import sirius.kernel.di.std.Register;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

/**
 * Provides an import job for {@link SQLCodeList code lists} stored in a JDBC database.
 */
@Register(classes = JobFactory.class, framework = SQLTenants.FRAMEWORK_TENANTS_JDBC)
@Permission(CodeListController.PERMISSION_MANAGE_CODELISTS)
public class SQLCodeListEntryImportJobFactory extends LineBasedImportJobFactory {

    /**
     * Contains the sql code list to import the code list entries into.
     */
    private SQLCodeListParameter codeListParameter = new SQLCodeListParameter("SQLCodeList");

    @Override
    protected void collectParameters(Consumer<Parameter<?, ?>> parameterCollector) {
        super.collectParameters(parameterCollector);

        parameterCollector.accept(codeListParameter);
    }

    @Override
    protected LineBasedImportJob<SQLCodeListEntry> createJob(ProcessContext process) {
        return new SQLCodeListEntryImportJob(fileParameter, ignoreEmptyParameter, getDictionary(), process);
    }

    protected class SQLCodeListEntryImportJob extends LineBasedImportJob<SQLCodeListEntry> {

        private SQLCodeList codeList;

        /**
         * Creates a new job for the given factory, name and process.
         *
         * @param fileParameter        the parameter which is used to derive the import file from
         * @param ignoreEmptyParameter the parameter which is used to determine if empty values should be ignored
         * @param dictionary           the import dictionary to use
         * @param process              the process context itself
         */
        private SQLCodeListEntryImportJob(VirtualObjectParameter fileParameter,
                                          BooleanParameter ignoreEmptyParameter,
                                          ImportDictionary dictionary,
                                          ProcessContext process) {
            super(fileParameter, ignoreEmptyParameter, SQLCodeListEntry.class, dictionary, process);
            codeList = process.require(codeListParameter);
        }

        @Override
        protected SQLCodeListEntry findAndLoad(Context ctx) {
            ctx.put(SQLCodeListEntry.CODE_LIST.toString(), codeList.getId());
            return super.findAndLoad(ctx);
        }

        /**
         * Completes the given entity and verifies the integrity of the data.
         *
         * @param entity the entity which has be loaded previously
         * @return the filled and verified entity
         */
        @Override
        protected SQLCodeListEntry fillAndVerify(SQLCodeListEntry entity) {
            setOrVerify(entity, entity.getCodeList(), codeList);
            return super.fillAndVerify(entity);
        }
    }

    @Override
    protected Class<? extends BaseEntity<?>> getImportType() {
        return SQLCodeListEntry.class;
    }

    @Override
    protected boolean hasPresetFor(Object targetObject) {
        return targetObject == SQLCodeListEntry.class;
    }

    @Nonnull
    @Override
    public String getName() {
        return "import-sql-code-list-entries";
    }
}
