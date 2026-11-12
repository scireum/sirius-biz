/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer.txn;

import sirius.biz.importer.ImportHandler;
import sirius.biz.importer.ImportHandlerFactory;
import sirius.biz.importer.ImporterContext;
import sirius.biz.importer.SQLEntityImportHandler;
import sirius.kernel.di.std.Register;

/**
 * Provides the import handler for {@link SQLImportTransactionTestEntity}.
 */
public class SQLImportTransactionTestEntityImportHandler
        extends SQLEntityImportHandler<SQLImportTransactionTestEntity> {

    /**
     * Creates the handler for {@link SQLImportTransactionTestEntity}.
     */
    @Register
    public static class SQLImportTransactionTestEntityImportHandlerFactory implements ImportHandlerFactory {

        @Override
        public boolean accepts(Class<?> type, ImporterContext context) {
            return type == SQLImportTransactionTestEntity.class;
        }

        @Override
        public ImportHandler<?> create(Class<?> type, ImporterContext context) {
            return new SQLImportTransactionTestEntityImportHandler(type, context);
        }
    }

    /**
     * Creates a new instance for the given type of entities and import context.
     *
     * @param clazz   the type of entities being handled
     * @param context the import context to use
     */
    protected SQLImportTransactionTestEntityImportHandler(Class<?> clazz, ImporterContext context) {
        super(clazz, context);
    }
}
