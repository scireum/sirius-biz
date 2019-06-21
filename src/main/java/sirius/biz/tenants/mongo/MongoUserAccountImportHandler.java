/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.mongo;

import sirius.biz.importer.ImportHandler;
import sirius.biz.importer.ImportHandlerFactory;
import sirius.biz.importer.ImporterContext;
import sirius.biz.importer.MongoEntityImportHandler;
import sirius.biz.model.LoginData;
import sirius.biz.tenants.UserAccountData;
import sirius.db.mixing.Property;
import sirius.kernel.commons.Context;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Register;

import java.util.Optional;

/**
 * Provides an import handler for {@link MongoUserAccount user accounts}.
 */
public class MongoUserAccountImportHandler extends MongoEntityImportHandler<MongoUserAccount> {

    /**
     * Provides the factory to instantiate this import handler.
     */
    @Register(framework = MongoTenants.FRAMEWORK_TENANTS_MONGO)
    public static class MongoUserAccountImportHandlerFactory implements ImportHandlerFactory {

        @Override
        public boolean accepts(Class<?> type) {
            return type == MongoUserAccount.class;
        }

        @Override
        public ImportHandler<?> create(Class<?> type, ImporterContext context) {
            return new MongoUserAccountImportHandler(type, context);
        }
    }

    /**
     * Creates a new instance for the given type of entities and import context.
     *
     * @param clazz   the type of entities being handled
     * @param context the import context to use
     */
    protected MongoUserAccountImportHandler(Class<?> clazz, ImporterContext context) {
        super(clazz, context);
    }

    @Override
    public Optional<MongoUserAccount> tryFind(Context data) {
        if (data.containsKey(MongoUserAccount.ID.getName())) {
            return mango.select(MongoUserAccount.class)
                        .eq(MongoUserAccount.ID, data.getValue(MongoUserAccount.ID.getName()).asString())
                        .one();
        }

        if (data.containsKey(MongoUserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.LOGIN)
                                                               .inner(LoginData.USERNAME)
                                                               .getName())) {
            return mango.select(MongoUserAccount.class)
                        .eq(MongoUserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.LOGIN).inner(LoginData.USERNAME),
                            data.getValue(MongoUserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.LOGIN)
                                                                            .inner(LoginData.USERNAME)
                                                                            .getName()))
                        .one();
        }

        return Optional.empty();
    }

    @Override
    protected boolean parseComplexProperty(MongoUserAccount entity, Property property, Value value, Context data) {
        return false;
    }
}
