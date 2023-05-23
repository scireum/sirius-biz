/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.mongo;

import com.fasterxml.jackson.core.JsonProcessingException;
import sirius.biz.analytics.flags.mongo.MongoPerformanceData;
import sirius.biz.codelists.LookupValue;
import sirius.biz.mongo.CustomSortValues;
import sirius.biz.mongo.SortField;
import sirius.biz.protocol.JournalData;
import sirius.biz.tenants.Tenant;
import sirius.biz.tenants.TenantUserManager;
import sirius.biz.tenants.UserAccount;
import sirius.biz.tenants.UserAccountData;
import sirius.biz.tycho.academy.OnboardingData;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.Index;
import sirius.db.mixing.annotations.Transient;
import sirius.db.mixing.annotations.TranslationSource;
import sirius.db.mongo.Mango;
import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Json;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.commons.ValueHolder;
import sirius.kernel.di.std.Framework;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.web.controller.Message;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Represents a user account which can log into the system.
 * <p>
 * Several users are grouped together by their company, which is referred to as {@link Tenant}.
 */
@Framework(MongoTenants.FRAMEWORK_TENANTS_MONGO)
@Index(name = "index_username",
        columns = "userAccountData_login_username",
        columnSettings = Mango.INDEX_ASCENDING,
        unique = true)
@TranslationSource(UserAccount.class)
@Index(name = "index_prefixes", columns = "searchPrefixes", columnSettings = Mango.INDEX_ASCENDING)
@Index(name = "index_sort", columns = "sortField_sortField", columnSettings = Mango.INDEX_ASCENDING)
public class MongoUserAccount extends MongoTenantAware implements UserAccount<String, MongoTenant>, CustomSortValues {

    @Part
    @Nullable
    private static MongoTenants tenants;

    private final UserAccountData userAccountData = new UserAccountData(this);
    private final JournalData journal = new JournalData(this);
    private final MongoPerformanceData performanceData = new MongoPerformanceData(this);
    private final OnboardingData onboardingData = new OnboardingData(this);

    public static final Mapping SORT_FIELD = Mapping.named("sortField");
    private final SortField sortField = new SortField(this);

    @Transient
    private ValueHolder<String> userIcon;

    @Override
    protected void addCustomSearchPrefixes(Consumer<String> tokenizer) {
        tokenizer.accept(getUserAccountData().getPerson().getFirstname());
        tokenizer.accept(getUserAccountData().getPerson().getLastname());
        tokenizer.accept(getUserAccountData().getLogin().getUsername());
        tokenizer.accept(getUserAccountData().getEmail());

        tenants.fetchCachedTenant(getTenant()).ifPresent(tenant -> {
            tokenizer.accept(tenant.getTenantData().getName());
            tokenizer.accept(tenant.getTenantData().getAccountNumber());
        });
    }

    @Override
    public void emitSortValues(Consumer<Object> sortValueConsumer) {
        tenants.fetchCachedTenant(getTenant()).ifPresent(tenant -> {
            sortValueConsumer.accept(tenant.getTenantData().getName());
        });

        sortValueConsumer.accept(getUserAccountData().getPerson().getLastname());
        sortValueConsumer.accept(getUserAccountData().getPerson().getFirstname());
        sortValueConsumer.accept(getUserAccountData().getLogin().getUsername());
    }

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
    @SuppressWarnings("squid:S1185")
    @Explain("This method must be overridden, because it is defined with a generic parameter in UserAccount")
    public void setId(String id) {
        super.setId(id);
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
    public Optional<String> getUserIcon() {
        if (userIcon == null) {
            LookupValue salutation = getUserAccountData().getPerson().getSalutation();
            userIcon = new ValueHolder<>(salutation.getTable()
                                                   .fetchField(salutation.getValue(), "icon")
                                                   .filter(Strings::isFilled)
                                                   .orElse(null));
        }

        return userIcon.asOptional();
    }

    @Override
    public Value readPreference(String key) {
        return Value.of(getUserAccountData().fetchUserPreferences().get(key));
    }

    @Override
    public void updatePreference(String key, Object value) {
        try {
            Map<String, Object> newPreferences = new HashMap<>(getUserAccountData().fetchUserPreferences());
            String updatedPreferencesAsString = computeUpdatedPreferences(newPreferences, key, value);
            mongo.update()
                 .set(MongoUserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.USER_PREFERENCES),
                      updatedPreferencesAsString)
                 .where(MongoUserAccount.ID, id)
                 .executeForOne(MongoUserAccount.class);
            TenantUserManager.flushCacheForUserAccount(this);
        } catch (JsonProcessingException e) {
            throw Exceptions.handle()
                            .to(Log.SYSTEM)
                            .error(e)
                            .withSystemErrorMessage("Failed to update user preference '%s' to '%s' for %s: %s (%s)",
                                                    key,
                                                    value,
                                                    getIdAsString())
                            .handle();
        }
    }

    private String computeUpdatedPreferences(Map<String, Object> preferences, String key, Object value)
            throws JsonProcessingException {
        if (Strings.isEmpty(value)) {
            preferences.remove(key);
        } else {
            preferences.put(key, value);
        }

        if (preferences.isEmpty()) {
            return null;
        } else {
            return Json.MAPPER.writeValueAsString(preferences);
        }
    }

    @Override
    public JournalData getJournal() {
        return journal;
    }

    @Override
    public String getRateLimitScope() {
        return getIdAsString();
    }

    @Override
    public UserAccountData getUserAccountData() {
        return userAccountData;
    }

    @Override
    public MongoPerformanceData getPerformanceData() {
        return performanceData;
    }

    @Override
    public OnboardingData getOnboardingData() {
        return onboardingData;
    }

    public SortField getSortField() {
        return sortField;
    }
}
