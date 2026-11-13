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
import sirius.biz.importer.MongoEntityImportHandler;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Register;

import java.util.Optional;

/**
 * Provides the import handler for {@link MongoImportTransactionTestEntity}.
 */
public class MongoImportTransactionTestEntityImportHandler
        extends MongoEntityImportHandler<MongoImportTransactionTestEntity> {

    /**
     * Creates the handler for {@link MongoImportTransactionTestEntity}.
     */
    @Register
    public static class MongoImportTransactionTestEntityImportHandlerFactory implements ImportHandlerFactory {

        @Override
        public boolean accepts(Class<?> type, ImporterContext context) {
            return type == MongoImportTransactionTestEntity.class;
        }

        @Override
        public ImportHandler<?> create(Class<?> type, ImporterContext context) {
            return new MongoImportTransactionTestEntityImportHandler(type, context);
        }
    }

    /**
     * Creates a new instance for the given type of entities and import context.
     *
     * @param clazz   the type of entities being handled
     * @param context the import context to use
     */
    protected MongoImportTransactionTestEntityImportHandler(Class<?> clazz, ImporterContext context) {
        super(clazz, context);
    }

    @Override
    protected Optional<MongoImportTransactionTestEntity> tryFindByExample(MongoImportTransactionTestEntity example) {
        if (Strings.isFilled(example.getId())) {
            return mango.find(MongoImportTransactionTestEntity.class, example.getId());
        }

        return Optional.empty();
    }
}
