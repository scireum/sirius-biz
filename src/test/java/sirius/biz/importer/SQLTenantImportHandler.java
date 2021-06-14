/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer;

import sirius.biz.tenants.TenantData;
import sirius.biz.tenants.jdbc.SQLTenant;
import sirius.db.jdbc.batch.FindQuery;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Register;

import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Provides a test importer used for SQL tenants and alsi in {@link ImporterSpec}.
 */
public class SQLTenantImportHandler extends SQLEntityImportHandler<SQLTenant> {

    @Register
    public static class SQLTenantImportHandlerFactory implements ImportHandlerFactory {

        @Override
        public boolean accepts(Class<?> type, ImporterContext context) {
            return type == SQLTenant.class;
        }

        @Override
        public ImportHandler<?> create(Class<?> type, ImporterContext context) {
            return new SQLTenantImportHandler(type, context);
        }
    }

    /**
     * Creates a new instance for the given type of entities and import context.
     *
     * @param clazz   the type of entities being handled
     * @param context the import context to use
     */
    protected SQLTenantImportHandler(Class<?> clazz, ImporterContext context) {
        super(clazz, context);
    }

    @Override
    protected void collectFindQueries(BiConsumer<Predicate<SQLTenant>, Supplier<FindQuery<SQLTenant>>> queryConsumer) {
        super.collectFindQueries(queryConsumer);
        queryConsumer.accept(tenant -> Strings.isFilled(tenant.getTenantData().getAccountNumber()),
                             () -> context.getBatchContext()
                                          .findQuery(SQLTenant.class,
                                                     SQLTenant.TENANT_DATA.inner(TenantData.ACCOUNT_NUMBER)));
    }
}
