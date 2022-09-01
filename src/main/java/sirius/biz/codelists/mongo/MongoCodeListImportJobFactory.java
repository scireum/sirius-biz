/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists.mongo;

import sirius.biz.codelists.CodeList;
import sirius.biz.codelists.CodeListController;
import sirius.biz.codelists.CodeListEntry;
import sirius.biz.importer.ImportContext;
import sirius.biz.jobs.StandardCategories;
import sirius.biz.jobs.batch.file.EntityImportJobFactory;
import sirius.biz.jobs.params.CodeListParameter;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.ProcessLink;
import sirius.db.mixing.BaseEntity;
import sirius.kernel.di.std.Register;
import sirius.web.http.QueryString;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Provides an import job for {@link MongoCodeList code lists} stored in MongoDB.
 */
@Register(framework = MongoCodeLists.FRAMEWORK_CODE_LISTS_MONGO)
@Permission(CodeListController.PERMISSION_MANAGE_CODELISTS)
public class MongoCodeListImportJobFactory extends EntityImportJobFactory {

    /**
     * Contains the mongo code list to import the code list entries into.
     */
    private final Parameter<CodeList> codeListParameter =
            new CodeListParameter("codeList", "$CodeList").markRequired().build();

    @Nonnull
    @Override
    public String getName() {
        return "import-mongo-code-list-entries";
    }

    @Override
    public String getCategory() {
        return StandardCategories.MISC;
    }

    @Override
    protected Class<? extends BaseEntity<?>> getImportType() {
        return MongoCodeListEntry.class;
    }

    @Override
    protected String createProcessTitle(Map<String, String> context) {
        return super.createProcessTitle(context) + codeListParameter.get(context)
                                                                    .map(codeList -> " - " + codeList.getCodeListData()
                                                                                                     .getName())
                                                                    .orElse("");
    }

    @Override
    protected void executeTask(ProcessContext process) throws Exception {
        process.addLink(new ProcessLink().withLabel("$CodeList")
                                         .withUri("/code-list/" + process.require(codeListParameter).getIdAsString()));
        super.executeTask(process);
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?>> parameterCollector) {
        parameterCollector.accept(codeListParameter);
        super.collectParameters(parameterCollector);
    }

    @Override
    protected void transferParameters(ImportContext context, ProcessContext processContext) {
        context.set(CodeListEntry.CODE_LIST, processContext.require(codeListParameter));
    }

    @Override
    protected boolean hasPresetFor(QueryString queryString, Object targetObject) {
        return targetObject instanceof MongoCodeList;
    }

    @Override
    protected void computePresetFor(QueryString queryString, Object targetObject, Map<String, Object> preset) {
        preset.put(codeListParameter.getName(), ((MongoCodeList) targetObject).getCodeListData().getCode());
    }
}
