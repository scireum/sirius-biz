/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.jdbc;

import sirius.biz.analytics.flags.jdbc.SQLPerformanceData;
import sirius.biz.protocol.JournalData;
import sirius.biz.tenants.Tenant;
import sirius.biz.tenants.UserAccount;
import sirius.biz.tenants.UserAccountData;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.Index;
import sirius.db.mixing.annotations.TranslationSource;
import sirius.kernel.commons.Explain;
import sirius.kernel.di.std.Framework;
import sirius.web.controller.Message;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Represents a user account which can log into the system.
 * <p>
 * Serveral users are grouped together by their company, which is referred to as {@link Tenant}.
 */
@Framework(SQLTenants.FRAMEWORK_TENANTS_JDBC)
@Index(name = "index_username", columns = "userAccountData_login_username", unique = true)
@TranslationSource(UserAccount.class)
public class SQLUserAccount extends SQLTenantAware implements UserAccount<Long, SQLTenant> {

    public static final Mapping USER_ACCOUNT_DATA = Mapping.named("userAccountData");
    private final UserAccountData userAccountData = new UserAccountData(this);

    /**
     * Used to record changes on fields of the user.
     */
    public static final Mapping JOURNAL = Mapping.named("journal");
    private final JournalData journal = new JournalData(this);

    private final SQLPerformanceData performanceData = new SQLPerformanceData(this);
    @Override
    public <A> Optional<A> tryAs(Class<A> adapterType) {
        if (getUserAccountData().is(adapterType)) {
            Optional<A> result = getUserAccountData().tryAs(adapterType);
            if (result.isPresent()) {
                return result;
            }
        }

        return super.tryAs(adapterType);
    }

    @Override
    public boolean is(Class<?> type) {
        return getUserAccountData().is(type) || super.is(type);
    }

    @Override
    public UserAccountData getUserAccountData() {
        return userAccountData;
    }

    @Override
    @SuppressWarnings("squid:S1185")
    @Explain("This method must be overridden, because it is defined with a generic parameter in UserAccount")
    public void setId(Long id) {
        super.setId(id);
    }

    @Override
    public JournalData getJournal() {
        return journal;
    }

    @Override
    public void addMessages(Consumer<Message> consumer) {
        getUserAccountData().addMessages(consumer);
    }

    @Override
    public String toString() {
        return userAccountData.toString();
    }

    @Override
    public String getRateLimitScope() {
        return getIdAsString();
    }

    @Override
    public SQLPerformanceData getPerformanceData() {
        return performanceData;
    }
}
