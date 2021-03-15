/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.mongo;

import sirius.biz.analytics.flags.PerformanceDataImportExtender;
import sirius.biz.importer.ImportContext;
import sirius.biz.importer.ImportHandler;
import sirius.biz.importer.ImportHandlerFactory;
import sirius.biz.importer.ImporterContext;
import sirius.biz.importer.MongoEntityImportHandler;
import sirius.biz.model.LoginData;
import sirius.biz.model.PermissionData;
import sirius.biz.model.PersonData;
import sirius.biz.tenants.Tenant;
import sirius.biz.tenants.TenantData;
import sirius.biz.tenants.TenantUserManager;
import sirius.biz.tenants.UserAccount;
import sirius.biz.tenants.UserAccountData;
import sirius.biz.tenants.jdbc.SQLUserAccount;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.Property;
import sirius.kernel.commons.Context;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.web.security.UserContext;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Provides an import handler for {@link MongoUserAccount user accounts}.
 */
public class MongoUserAccountImportHandler extends MongoEntityImportHandler<MongoUserAccount> {

    @Part
    @Nullable
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
    protected MongoUserAccount loadForFind(Context data) {
        MongoUserAccount userAccount = load(data,
                                            MongoUserAccount.ID,
                                            MongoUserAccount.TENANT,
                                            SQLUserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.EMAIL),
                                            MongoUserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.LOGIN)
                                                                              .inner(LoginData.USERNAME));

        if (UserContext.getCurrentUser().hasPermission(TenantUserManager.PERMISSION_SYSTEM_TENANT_MEMBER)) {
            load(data, userAccount, MongoUserAccount.TENANT);
        }

        if (userAccount.getTenant().isEmpty()) {
            userAccount.getTenant().setValue(tenants.getRequiredTenant());
        }

        userAccount.getUserAccountData().transferEmailToLoginIfEmpty();

        return userAccount;
    }

    @Override
    public MongoUserAccount load(Context data, MongoUserAccount entity) {
        MongoUserAccount result = super.load(data, entity);

        if (UserContext.getCurrentUser().hasPermission(TenantUserManager.PERMISSION_SYSTEM_TENANT_MEMBER)) {
            load(data, result, SQLUserAccount.TENANT);
        }

        return result;
    }

    @Override
    protected Optional<MongoUserAccount> tryFindByExample(MongoUserAccount example) {
        if (Strings.isFilled(example.getId())) {
            return mango.select(MongoUserAccount.class)
                        .eq(MongoUserAccount.ID, example.getId())
                        .eq(MongoUserAccount.TENANT, example.getTenant())
                        .one();
        }

        if (Strings.isFilled(example.getUserAccountData().getLogin().getUsername())) {
            return mango.select(MongoUserAccount.class)
                        .eq(MongoUserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.LOGIN).inner(LoginData.USERNAME),
                            example.getUserAccountData().getLogin().getUsername())
                        .eq(MongoUserAccount.TENANT, example.getTenant())
                        .one();
        }

        return Optional.empty();
    }

    @Override
    protected boolean parseComplexProperty(MongoUserAccount entity, Property property, Value value, Context data) {
        if (UserAccount.TENANT.getName().equals(property.getName())) {
            ImportContext lookupContext = ImportContext.create();
            lookupContext.set(Tenant.TENANT_DATA.inner(TenantData.ACCOUNT_NUMBER), value.get());

            ImportHandler<MongoTenant> importHandler = context.findHandler(MongoTenant.class);
            entity.getTenant()
                  .setValue(importHandler.tryFind(lookupContext)
                                         .orElseThrow(() -> Exceptions.createHandled()
                                                                      .withSystemErrorMessage(
                                                                              "Cannot find a tenant with account number: '%s'",
                                                                              value.getString())
                                                                      .handle()));
            return true;
        }

        return super.parseComplexProperty(entity, property, value, data);
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
