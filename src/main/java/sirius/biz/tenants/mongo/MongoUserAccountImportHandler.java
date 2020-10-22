/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.mongo;

import sirius.biz.analytics.flags.PerformanceDataImportExtender;
import sirius.biz.importer.ImportHandler;
import sirius.biz.importer.ImportHandlerFactory;
import sirius.biz.importer.ImporterContext;
import sirius.biz.importer.MongoEntityImportHandler;
import sirius.biz.model.LoginData;
import sirius.biz.model.PermissionData;
import sirius.biz.model.PersonData;
import sirius.biz.tenants.UserAccountData;
import sirius.db.mixing.Mapping;
import sirius.kernel.commons.Context;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Provides an import handler for {@link MongoUserAccount user accounts}.
 */
public class MongoUserAccountImportHandler extends MongoEntityImportHandler<MongoUserAccount> {

    @Part
    protected MongoTenants tenants;

    /**
     * Provides the factory to instantiate this import handler.
     */
    @Register(framework = MongoTenants.FRAMEWORK_TENANTS_MONGO)
    public static class MongoUserAccountImportHandlerFactory implements ImportHandlerFactory {

        @Override
        public boolean accepts(Class<?> type, ImporterContext context) {
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
                        .eq(MongoUserAccount.TENANT, tenants.getRequiredTenant())
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
                        .eq(MongoUserAccount.TENANT, tenants.getRequiredTenant())
                        .one();
        }

        return Optional.empty();
    }

    @Override
    protected void collectDefaultExportableMappings(BiConsumer<Integer, Mapping> collector) {
        collector.accept(100,
                         MongoUserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.LOGIN).inner(LoginData.USERNAME));
        collector.accept(110, MongoUserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.EMAIL));
        collector.accept(120,
                         MongoUserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.PERSON).inner(PersonData.SALUTATION));
        collector.accept(130, MongoUserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.PERSON).inner(PersonData.TITLE));
        collector.accept(140,
                         MongoUserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.PERSON).inner(PersonData.FIRSTNAME));
        collector.accept(150,
                         MongoUserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.PERSON).inner(PersonData.LASTNAME));
        collector.accept(200,
                         MongoUserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.PERMISSIONS)
                                                           .inner(PermissionData.PERMISSIONS));
        collector.accept(210,
                         MongoUserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.LOGIN)
                                                           .inner(LoginData.ACCOUNT_LOCKED));
        collector.accept(220, PerformanceDataImportExtender.PERFORMANCE_FLAGS);
        collector.accept(300,
                         MongoUserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.LOGIN).inner(LoginData.LAST_SEEN));
        collector.accept(305,
                         MongoUserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.LOGIN).inner(LoginData.LAST_LOGIN));
        collector.accept(310,
                         MongoUserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.LOGIN)
                                                           .inner(LoginData.NUMBER_OF_LOGINS));
        collector.accept(320,
                         MongoUserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.LOGIN)
                                                           .inner(LoginData.LAST_EXTERNAL_LOGIN));
        collector.accept(330,
                         MongoUserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.LOGIN)
                                                           .inner(LoginData.LAST_PASSWORD_CHANGE));
    }
}
