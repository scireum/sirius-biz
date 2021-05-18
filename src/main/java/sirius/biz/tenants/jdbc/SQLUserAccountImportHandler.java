/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.jdbc;

import sirius.biz.analytics.flags.PerformanceDataImportExtender;
import sirius.biz.importer.ImportContext;
import sirius.biz.importer.ImportHandler;
import sirius.biz.importer.ImportHandlerFactory;
import sirius.biz.importer.ImporterContext;
import sirius.biz.importer.SQLEntityImportHandler;
import sirius.biz.model.LoginData;
import sirius.biz.model.PermissionData;
import sirius.biz.model.PersonData;
import sirius.biz.tenants.Tenant;
import sirius.biz.tenants.TenantData;
import sirius.biz.tenants.TenantUserManager;
import sirius.biz.tenants.UserAccountData;
import sirius.biz.web.TenantAware;
import sirius.db.jdbc.batch.FindQuery;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.Property;
import sirius.kernel.commons.Context;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.security.UserContext;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Provides an import handler for {@link SQLUserAccount user accounts}.
 */
public class SQLUserAccountImportHandler extends SQLEntityImportHandler<SQLUserAccount> {

    @Part
    @Nullable
    private static SQLTenants tenants;

    /**
     * Provides the factory to instantiate this import handler.
     */
    @Register(framework = SQLTenants.FRAMEWORK_TENANTS_JDBC)
    public static class SQLUserAccountImportHandlerFactory implements ImportHandlerFactory {

        @Override
        public boolean accepts(Class<?> type, ImporterContext context) {
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
        super.collectFindQueries(queryConsumer);
        queryConsumer.accept(user -> Strings.isFilled(user.getUserAccountData().getLogin().getUsername()),
                             () -> context.getBatchContext()
                                          .findQuery(SQLUserAccount.class,
                                                     TenantAware.TENANT,
                                                     SQLUserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.LOGIN)
                                                                                     .inner(LoginData.USERNAME)));
    }

    @Override
    protected void collectDefaultExportableMappings(BiConsumer<Integer, Mapping> collector) {
        collector.accept(100, SQLUserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.LOGIN).inner(LoginData.USERNAME));
        collector.accept(110, SQLUserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.EMAIL));
        collector.accept(120,
                         SQLUserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.PERSON).inner(PersonData.SALUTATION));
        collector.accept(130, SQLUserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.PERSON).inner(PersonData.TITLE));
        collector.accept(140,
                         SQLUserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.PERSON).inner(PersonData.FIRSTNAME));
        collector.accept(150,
                         SQLUserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.PERSON).inner(PersonData.LASTNAME));
        collector.accept(200,
                         SQLUserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.PERMISSIONS)
                                                         .inner(PermissionData.PERMISSIONS));
        collector.accept(210,
                         SQLUserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.LOGIN).inner(LoginData.ACCOUNT_LOCKED));
        collector.accept(220, PerformanceDataImportExtender.PERFORMANCE_FLAGS);
        collector.accept(300, SQLUserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.LOGIN).inner(LoginData.LAST_SEEN));
        collector.accept(305,
                         SQLUserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.LOGIN).inner(LoginData.LAST_LOGIN));
        collector.accept(310,
                         SQLUserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.LOGIN)
                                                         .inner(LoginData.NUMBER_OF_LOGINS));
        collector.accept(320,
                         SQLUserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.LOGIN)
                                                         .inner(LoginData.LAST_EXTERNAL_LOGIN));
        collector.accept(330,
                         SQLUserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.LOGIN)
                                                         .inner(LoginData.LAST_PASSWORD_CHANGE));
    }

    @Override
    protected SQLUserAccount loadForFind(Context data) {
        SQLUserAccount account = super.loadForFind(data);

        if (UserContext.getCurrentUser().hasPermission(TenantUserManager.PERMISSION_SYSTEM_TENANT_MEMBER)) {
            load(data, account, TenantAware.TENANT);
        }

        if (account.getTenant().isEmpty()) {
            account.getTenant().setValue(tenants.getRequiredTenant());
        }

        load(data, account, SQLUserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.EMAIL));
        account.getUserAccountData().transferEmailToLoginIfEmpty();

        return account;
    }

    @Override
    protected boolean parseComplexProperty(SQLUserAccount entity, Property property, Value value, Context data) {
        if (TenantAware.TENANT.getName().equals(property.getName())) {
            ImportContext lookupContext = ImportContext.create();
            lookupContext.set(Tenant.TENANT_DATA.inner(TenantData.ACCOUNT_NUMBER), value.get());

            ImportHandler<SQLTenant> importHandler = context.findHandler(SQLTenant.class);
            Optional<SQLTenant> tenant = importHandler.tryFind(lookupContext);
            if (tenant.isPresent()) {
                entity.getTenant().setValue(tenant.get());
                return true;
            }
        }

        return super.parseComplexProperty(entity, property, value, data);
    }

    @Override
    public SQLUserAccount load(Context data, SQLUserAccount entity) {
        SQLUserAccount result = super.load(data, entity);

        if (UserContext.getCurrentUser().hasPermission(TenantUserManager.PERMISSION_SYSTEM_TENANT_MEMBER)) {
            load(data, result, TenantAware.TENANT);
        }

        return result;
    }
}
