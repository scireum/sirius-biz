/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists.jdbc;

import sirius.biz.codelists.CodeListEntryData;
import sirius.biz.importer.ImportHandler;
import sirius.biz.importer.ImportHandlerFactory;
import sirius.biz.importer.ImporterContext;
import sirius.biz.importer.SQLEntityImportHandler;
import sirius.db.jdbc.batch.FindQuery;
import sirius.db.mixing.Mapping;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Register;

import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Provides an import handler for {@link SQLCodeListEntry code list entries}.
 */
public class SQLCodeListEntryImportHandler extends SQLEntityImportHandler<SQLCodeListEntry> {

    /**
     * Provides the factory to instantiate this import handler.
     */
    @Register(framework = SQLCodeLists.FRAMEWORK_CODE_LISTS_JDBC)
    public static class SQLCodeListImportHandlerFactory implements ImportHandlerFactory {

        @Override
        public boolean accepts(Class<?> type, ImporterContext context) {
            return type == SQLCodeListEntry.class;
        }

        @Override
        public ImportHandler<?> create(Class<?> type, ImporterContext context) {
            return new SQLCodeListEntryImportHandler(type, context);
        }
    }

    /**
     * Creates a new instance for the given type of entities and import context.
     *
     * @param clazz   the type of entities being handled
     * @param context the import context to use
     */
    protected SQLCodeListEntryImportHandler(Class<?> clazz, ImporterContext context) {
        super(clazz, context);
    }

    @Override
    protected void collectFindQueries(BiConsumer<Predicate<SQLCodeListEntry>, Supplier<FindQuery<SQLCodeListEntry>>> queryConsumer) {
        queryConsumer.accept(sqlCodeListEntry -> Strings.isFilled(sqlCodeListEntry.getCodeListEntryData().getCode())
                                                 && sqlCodeListEntry.getCodeList().isFilled(),
                             () -> context.getBatchContext()
                                          .findQuery(SQLCodeListEntry.class,
                                                     SQLCodeListEntry.CODE_LIST_ENTRY_DATA.inner(CodeListEntryData.CODE),
                                                     SQLCodeListEntry.CODE_LIST));
    }

    @Override
    protected void collectDefaultExportableMappings(BiConsumer<Integer, Mapping> collector) {
        collector.accept(100, SQLCodeListEntry.CODE_LIST_ENTRY_DATA.inner(CodeListEntryData.PRIORITY));
        collector.accept(110, SQLCodeListEntry.CODE_LIST_ENTRY_DATA.inner(CodeListEntryData.CODE));
        collector.accept(120, SQLCodeListEntry.CODE_LIST_ENTRY_DATA.inner(CodeListEntryData.VALUE));
        collector.accept(130, SQLCodeListEntry.CODE_LIST_ENTRY_DATA.inner(CodeListEntryData.ADDITIONAL_VALUE));
        collector.accept(140, SQLCodeListEntry.CODE_LIST_ENTRY_DATA.inner(CodeListEntryData.DESCRIPTION));
    }
}
