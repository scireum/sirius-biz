/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists.mongo;

import sirius.biz.codelists.CodeListEntryData;
import sirius.biz.importer.ImportHandler;
import sirius.biz.importer.ImportHandlerFactory;
import sirius.biz.importer.ImporterContext;
import sirius.biz.importer.MongoEntityImportHandler;
import sirius.db.mixing.Mapping;
import sirius.kernel.commons.Context;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Register;

import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Provides an import handler for {@link MongoCodeListEntry code list entries}.
 */
public class MongoCodeListEntryImportHandler extends MongoEntityImportHandler<MongoCodeListEntry> {

    /**
     * Provides the factory to instantiate this import handler.
     */
    @Register(framework = MongoCodeLists.FRAMEWORK_CODE_LISTS_MONGO)
    public static class MongoCodeListImportHandlerFactory implements ImportHandlerFactory {

        @Override
        public boolean accepts(Class<?> type, ImporterContext context) {
            return type == MongoCodeListEntry.class;
        }

        @Override
        public ImportHandler<?> create(Class<?> type, ImporterContext context) {
            return new MongoCodeListEntryImportHandler(type, context);
        }
    }

    /**
     * Creates a new instance for the given type of entities and import context.
     *
     * @param clazz   the type of entities being handled
     * @param context the import context to use
     */
    protected MongoCodeListEntryImportHandler(Class<?> clazz, ImporterContext context) {
        super(clazz, context);
    }

    @Override
    protected MongoCodeListEntry loadForFind(Context data) {
        return load(data,
                    MongoCodeListEntry.CODE_LIST,
                    MongoCodeListEntry.CODE_LIST_ENTRY_DATA.inner(CodeListEntryData.CODE));
    }

    @Override
    protected Optional<MongoCodeListEntry> tryFindByExample(MongoCodeListEntry example) {
        if (example.getCodeList().isFilled() && Strings.isFilled(example.getCodeListEntryData().getCode())) {
            return mango.select(MongoCodeListEntry.class)
                        .eq(MongoCodeListEntry.CODE_LIST_ENTRY_DATA.inner(CodeListEntryData.CODE),
                            example.getCodeListEntryData().getCode())
                        .eq(MongoCodeListEntry.CODE_LIST, example.getCodeList())
                        .one();
        }

        return Optional.empty();
    }

    @Override
    protected void collectDefaultExportableMappings(BiConsumer<Integer, Mapping> collector) {
        collector.accept(100, MongoCodeListEntry.CODE_LIST_ENTRY_DATA.inner(CodeListEntryData.PRIORITY));
        collector.accept(110, MongoCodeListEntry.CODE_LIST_ENTRY_DATA.inner(CodeListEntryData.CODE));
        collector.accept(120, MongoCodeListEntry.CODE_LIST_ENTRY_DATA.inner(CodeListEntryData.VALUE));
        collector.accept(130, MongoCodeListEntry.CODE_LIST_ENTRY_DATA.inner(CodeListEntryData.ADDITIONAL_VALUE));
        collector.accept(140, MongoCodeListEntry.CODE_LIST_ENTRY_DATA.inner(CodeListEntryData.DESCRIPTION));
    }
}
