/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists.mongo;

import sirius.biz.codelists.CodeListController;
import sirius.biz.jobs.JobFactory;
import sirius.biz.jobs.batch.file.EntityImportJob;
import sirius.biz.jobs.batch.file.EntityImportJobFactory;
import sirius.biz.jobs.params.CodeListParameter;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.process.ProcessContext;
import sirius.biz.tenants.mongo.MongoTenants;
import sirius.db.mixing.BaseEntity;
import sirius.kernel.commons.Context;
import sirius.kernel.di.std.Register;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Provides an import job for {@link MongoCodeList code lists} stored in MongoDB.
 */
@Register(classes = JobFactory.class, framework = MongoTenants.FRAMEWORK_TENANTS_MONGO)
@Permission(CodeListController.PERMISSION_MANAGE_CODELISTS)
public class MongoCodeListEntryImportJobFactory extends EntityImportJobFactory {

    /**
     * Contains the mongo code list to import the code list entries into.
     */
    private CodeListParameter<String, MongoCodeList> codeListParameter =
            new CodeListParameter<>("codeList", "$CodeList");

    @Override
    protected void collectParameters(Consumer<Parameter<?, ?>> parameterCollector) {
        super.collectParameters(parameterCollector);

        parameterCollector.accept(codeListParameter);
    }

    @Override
    protected EntityImportJob<MongoCodeListEntry> createJob(ProcessContext process) {
        return new MongoCodeListEntryImportJob(process);
    }

    protected class MongoCodeListEntryImportJob extends EntityImportJob<MongoCodeListEntry> {

        private MongoCodeList codeList;

        /**
         * Creates a new job for the given factory, name and process.
         *
         * @param process the process context itself
         */
        private MongoCodeListEntryImportJob(ProcessContext process) {
            super(fileParameter,
                  ignoreEmptyParameter,
                  importModeParameter,
                  MongoCodeListEntry.class,
                  getDictionary(),
                  process);
            codeList = process.require(codeListParameter);
        }

        @Override
        protected MongoCodeListEntry findAndLoad(Context ctx) {
            ctx.put(MongoCodeListEntry.CODE_LIST.toString(), codeList.getId());
            return super.findAndLoad(ctx);
        }

        @Override
        protected MongoCodeListEntry fillAndVerify(MongoCodeListEntry entity) {
            setOrVerify(entity, entity.getCodeList(), codeList);
            return super.fillAndVerify(entity);
        }
    }

    @Override
    protected Class<? extends BaseEntity<?>> getImportType() {
        return MongoCodeListEntry.class;
    }

    @Nonnull
    @Override
    public String getName() {
        return "import-mongo-code-list-entries";
    }

    @Override
    protected boolean hasPresetFor(Object targetObject) {
        return targetObject instanceof MongoCodeList;
    }

    @Override
    protected void computePresetFor(Object targetObject, Map<String, Object> preset) {
        preset.put(codeListParameter.getName(), ((MongoCodeList) targetObject).getId());
    }
}
