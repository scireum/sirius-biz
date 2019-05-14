/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.jdbc;

import sirius.biz.importer.ImportHandler;
import sirius.biz.importer.ImportHandlerFactory;
import sirius.biz.importer.ImporterContext;
import sirius.biz.importer.SQLEntityImportHandler;
import sirius.biz.model.LoginData;
import sirius.biz.tenants.UserAccountData;
import sirius.db.jdbc.batch.FindQuery;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Register;

import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Provides an import handler for {@link SQLUserAccount user accounts}.
 */
public class SQLUserAccountImportHandler extends SQLEntityImportHandler<SQLUserAccount> {

    /**
     * Provides the factory to instantiate this import handler.
     */
    @Register(framework = SQLTenants.FRAMEWORK_TENANTS_JDBC)
    public static class SQLUserAccountImportHandlerFactory implements ImportHandlerFactory {

        @Override
        public boolean accepts(Class<?> type) {
            return type == SQLUserAccount.class;
        }

        @Override
        public ImportHandler<?> create(Class<?> type, ImporterContext context) {
            return new SQLUserAccountImportHandler(type, context);
        }
    }

    /**
     * Creates a new instance for the given type of entities and import context.
     *
     * @param clazz   the type of entities being handled
     * @param context the import context to use
     */
    protected SQLUserAccountImportHandler(Class<?> clazz, ImporterContext context) {
        super(clazz, context);
    }

    @Override
    protected void collectFindQueries(BiConsumer<Predicate<SQLUserAccount>, Supplier<FindQuery<SQLUserAccount>>> queryConsumer) {
        queryConsumer.accept(user -> !user.isNew(),
                             () -> context.getBatchContext().findQuery(SQLUserAccount.class, SQLUserAccount.ID));
        queryConsumer.accept(user -> Strings.isFilled(user.getUserAccountData().getLogin().getUsername()),
                             () -> context.getBatchContext()
                                          .findQuery(SQLUserAccount.class,
                                                     SQLUserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.LOGIN)
                                                                                     .inner(LoginData.USERNAME)));
    }

    @Override
    protected boolean isChanged(SQLUserAccount entity) {
        return super.isChanged(entity)
                || entity.isChanged(SQLUserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.LOGIN).inner(LoginData.GENERATED_PASSWORD));
    }
}
