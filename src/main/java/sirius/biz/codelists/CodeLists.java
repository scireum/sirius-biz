/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists;

import sirius.biz.codelists.jdbc.SQLCodeListEntry;
import sirius.biz.tenants.Tenant;
import sirius.biz.tenants.Tenants;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.BaseMapper;
import sirius.db.mixing.Mixing;
import sirius.db.mixing.query.Query;
import sirius.kernel.Sirius;
import sirius.kernel.cache.Cache;
import sirius.kernel.cache.CacheManager;
import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.ValueHolder;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.kernel.health.Log;
import sirius.kernel.nls.NLS;
import sirius.kernel.settings.Extension;
import sirius.pasta.noodle.sandbox.NoodleSandbox;
import sirius.web.security.ScopeInfo;
import sirius.web.security.UserContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides a framework to map string values (<tt>codes</tt>) to values defined by the user.
 * <p>
 * A code list is referenced by its unique name (code) and can be used to map codes to user defined texts. Additionally
 * a second values (<tt>additionalValue</tt>) can be stored per code.
 * <p>
 * The required data is stored via {@link CodeList} and {@link CodeListEntry}. The {@link CodeListController} is used
 * to provide an administration GUI for the user.
 *
 * @param <I> the type of database IDs used by the concrete implementation
 * @param <L> the effective entity type used to represent code lists
 * @param <E> the effective entity type used to represent code list entries
 */
public abstract class CodeLists<I extends Serializable, L extends BaseEntity<I> & CodeList, E extends BaseEntity<I> & CodeListEntry<I, L>> {

    protected static final String CONFIG_EXTENSION_CODE_LISTS = "code-lists";
    protected static final String CONFIG_KEY_NAME = "name";
    protected static final String CONFIG_KEY_DESCRIPTION = "description";
    protected static final String CONFIG_KEY_AUTOFILL = "autofill";
    protected static final String CONFIG_KEY_GLOBAL = "global";
    protected Cache<String, ValueHolder<Tuple<String, String>>> valueCache =
            CacheManager.createCoherentCache("codelists-values");

    protected static final Log LOG = Log.get("codelists");

    protected Map<String, Boolean> codeListGlobalFlag = new ConcurrentHashMap<>();

    @Part
    private Tenants<?, ?, ?> tenants;

    @Part
    private Mixing mixing;

    private BaseMapper<L, ?, ?> listMapper;
    private BaseMapper<E, ?, ?> entryMapper;

    /**
     * Returns the effective entity class used to represent code lists.
     *
     * @return the effective implementation of {@link CodeList}
     */
    protected abstract Class<L> getListType();

    /**
     * Returns the effective entity class used to represent code list entries.
     *
     * @return the effective implementation of {@link CodeListEntry}
     */
    protected abstract Class<E> getEntryType();

    @SuppressWarnings("unchecked")
    private <Q extends Query<Q, L, ?>> Q createListQuery() {
        if (listMapper == null) {
            listMapper = mixing.getDescriptor(getListType()).getMapper();
        }
        return (Q) listMapper.select(getListType());
    }

    @SuppressWarnings("unchecked")
    private <Q extends Query<Q, E, ?>> Q createEntryQuery() {
        if (entryMapper == null) {
            entryMapper = mixing.getDescriptor(getEntryType()).getMapper();
        }
        return (Q) entryMapper.select(getEntryType());
    }

    protected Query<?, E, ?> queryEntry(L list, @Nonnull String code) {
        return createEntryQuery().eq(SQLCodeListEntry.CODE_LIST, list)
                                 .eq(SQLCodeListEntry.CODE_LIST_ENTRY_DATA.inner(CodeListEntryData.CODE), code);
    }

    /**
     * Finds a {@link CodeList} that matches the given name and belongs to the current tenant.
     *
     * @param codeListName The code of the {@link CodeList}
     * @return An optional of the matching {@link CodeList}, or null if there is no
     * codeList with the given name
     */
    public Optional<L> findCodelist(@Nonnull String codeListName) {
        if (Strings.isEmpty(codeListName)) {
            throw new IllegalArgumentException("codeList must not be empty");
        }

        Tenant<?> currentTenant = getRequiredTenant(codeListName);

        L codeList = createListQuery().eq(CodeList.TENANT, currentTenant)
                                      .eq(CodeList.CODE_LIST_DATA.inner(CodeListData.CODE), codeListName)
                                      .queryFirst();

        return Optional.ofNullable(codeList);
    }

    @Nonnull
    private L findOrCreateCodelist(@Nonnull String codeListName) {
        Optional<L> optionalCodeList = findCodelist(codeListName);

        Tenant<?> currentTenant = getRequiredTenant(codeListName);

        if (optionalCodeList.isPresent()) {
            return optionalCodeList.get();
        }
        Extension ext = getCodeListConfig(codeListName);

        L codeList = createCodeList(currentTenant, codeListName);
        if (ext != null && !ext.isDefault()) {
            codeList.getCodeListData().setName(ext.get(CONFIG_KEY_NAME).asString());
            codeList.getCodeListData().setDescription(ext.get(CONFIG_KEY_DESCRIPTION).asString());
            codeList.getCodeListData().setAutofill(ext.get(CONFIG_KEY_AUTOFILL).asBoolean());
        }

        codeList.getMapper().update(codeList);

        return codeList;
    }

    @Nonnull
    private L createCodeList(@Nonnull Tenant<?> tenant, String codeListName) {
        try {
            L codeList = getListType().getDeclaredConstructor().newInstance();
            codeList.withTenant(tenant);
            codeList.getCodeListData().setCode(codeListName);
            codeList.getCodeListData().setName(codeListName);
            return codeList;
        } catch (Exception e) {
            throw Exceptions.handle(LOG, e);
        }
    }

    /**
     * Determines the effective current tenant.
     * <p>
     * This also gracefully handles scopes other than the current tenant as long as there is an adapter to make it
     * match a {@link CodeListTenantProvider}.
     * <p>
     * Note that "global" code lists are always stored in the system tenant and shared accross all tenants.
     *
     * @param codeListName the name of the code list to determine if the code list is global and doesn't require a
     *                     tenant at all
     * @return optional of the current tenant to be used to determine which code lists to operate on.
     */
    @SuppressWarnings("unchecked")
    protected Optional<Tenant<?>> getCurrentTenant(String codeListName) {
        if (isCodeListGlobal(codeListName)) {
            return (Optional<Tenant<?>>) tenants.fetchCachedTenant(tenants.getTenantUserManager().getSystemTenantId());
        }

        if (UserContext.getCurrentScope() == ScopeInfo.DEFAULT_SCOPE) {
            return (Optional<Tenant<?>>) tenants.getCurrentTenant();
        }

        Tenant<?> currentTenant = UserContext.getCurrentScope().as(CodeListTenantProvider.class).getCurrentTenant();

        if (currentTenant == null) {
            throw Exceptions.handle()
                            .to(LOG)
                            .withSystemErrorMessage("Cannot determine the effective tenant for scope %!",
                                                    UserContext.getCurrentScope().getScopeId())
                            .handle();
        }

        return Optional.of(currentTenant);
    }

    /**
     * Determines the effective current tenant and fails if none is present.
     * <p>
     * This uses the same lookup logic as {@link #getCurrentTenant(String)}.
     *
     * @param codeListName the name of the code list to determine if the code list is global and doesn't require a
     *                     tenant at all
     * @return the currently active tenant to use for code list operations
     * @throws HandledException in case a tenant is required and none is available
     */
    protected Tenant<?> getRequiredTenant(String codeListName) {
        return getCurrentTenant(codeListName).orElseThrow(this::warnAboutMissingTenant);
    }

    protected HandledException warnAboutMissingTenant() {
        return Exceptions.handle().to(LOG).withNLSKey("CodeLists.noCurrentTenant").handle();
    }

    private boolean isCodeListGlobal(String codeListName) {
        return codeListGlobalFlag.computeIfAbsent(codeListName, ignored -> {
            Extension ext = getCodeListConfig(codeListName);
            boolean isGlobal = ext.get(CONFIG_KEY_GLOBAL).asBoolean();
            if (isGlobal && ext.get(CONFIG_KEY_AUTOFILL).asBoolean()) {
                LOG.WARN("The code list '%s' is 'global' and has 'autofill' enabled. This is a dangerous combination!",
                         codeListName);
            }
            return isGlobal;
        });
    }

    private Extension getCodeListConfig(String codeListName) {
        return Sirius.getSettings().getExtension(CONFIG_EXTENSION_CODE_LISTS, codeListName);
    }

    /**
     * Returns the value and the additionalValue associated with the given code.
     * <p>
     * If no matching entry exists, the code itself will be returned as value and the additional value (the second of
     * the tuple) will be <tt>null</tt>.
     *
     * @param codeListName the code list to search in
     * @param code         the code to lookup
     * @return the value and the additional value associated with the given code, wrapped as tuple
     */
    public Tuple<String, String> getValues(@Nonnull String codeListName, @Nonnull String code) {
        return tryGetValues(codeListName, code).orElseGet(() -> Tuple.create(code, null));
    }

    /**
     * Returns the value and the additionalValue associated with the given code.
     *
     * @param codeListName the code list to search in
     * @param code         the code to lookup
     * @return the value and the additional value associated with the given code or the code itself and <tt>null</tt>
     * (if {@link CodeListData#AUTO_FILL} is <tt>true</tt>) or an empty optional otherwise
     */
    public Optional<Tuple<String, String>> tryGetValues(@Nonnull String codeListName, @Nullable String code) {
        if (Strings.isEmpty(code)) {
            return Optional.empty();
        }
        return getCurrentTenant(codeListName).flatMap(tenant -> fetchValueFromCache(tenant, codeListName, code));
    }

    private Optional<Tuple<String, String>> fetchValueFromCache(@Nonnull Tenant<?> tenant,
                                                                String codeListName,
                                                                String code) {
        return valueCache.get(tenant.getIdAsString() + codeListName + "|" + code + "|-",
                              ignored -> loadValues(codeListName, code)).asOptional();
    }

    @Nonnull
    private ValueHolder<Tuple<String, String>> loadValues(String codeListName, String code) {
        E codeListEntry = loadEntry(codeListName, code);

        if (codeListEntry == null) {
            return ValueHolder.of(null);
        }

        return ValueHolder.of(Tuple.create(codeListEntry.getCodeListEntryData().getValue().getFallback(),
                                           codeListEntry.getCodeListEntryData().getAdditionalValue().getFallback()));
    }

    /**
     * Returns the value and the additionalValue associated with the given code.
     * <p>
     * If no matching entry exists, the code itself will be returned as value and the additional value (the second of
     * the tuple) will be <tt>null</tt>.
     *
     * @param codeListName the code list to search in
     * @param code         the code to lookup
     * @param language     the language code to lookup
     * @return the value and the additional value in the given language associated with the given code, wrapped as tuple
     */
    public Tuple<String, String> getTranslatedValues(@Nonnull String codeListName,
                                                     @Nonnull String code,
                                                     String language) {
        return tryGetTranslatedValues(codeListName, code, language).orElseGet(() -> Tuple.create(code, null));
    }

    /**
     * Returns the value and the additionalValue associated with the given code in the given language.
     *
     * @param codeListName the code list to search in
     * @param code         the code to lookup
     * @param language     the language code to lookup
     * @return the value and the additional value in the given language associated with the given code or the code
     * itself and <tt>null</tt> (if {@link CodeListData#AUTO_FILL} is <tt>true</tt>) or an empty optional otherwise
     */
    public Optional<Tuple<String, String>> tryGetTranslatedValues(@Nonnull String codeListName,
                                                                  @Nullable String code,
                                                                  @Nullable String language) {
        if (Strings.isEmpty(code)) {
            return Optional.empty();
        }
        if (Strings.isEmpty(language)) {
            return tryGetValues(codeListName, code);
        }
        return getCurrentTenant(codeListName).flatMap(tenant -> fetchTranslatedValueFromCache(tenant,
                                                                                              codeListName,
                                                                                              code,
                                                                                              language));
    }

    private Optional<Tuple<String, String>> fetchTranslatedValueFromCache(@Nonnull Tenant<?> tenant,
                                                                          String codeListName,
                                                                          String code,
                                                                          String language) {
        return valueCache.get(tenant.getIdAsString() + codeListName + "|" + code + "|" + language,
                              ignored -> loadTranslatedValues(codeListName, code, language)).asOptional();
    }

    @Nonnull
    private ValueHolder<Tuple<String, String>> loadTranslatedValues(String codeListName, String code, String language) {
        if (Strings.isEmpty(language)) {
            return loadValues(codeListName, code);
        }

        E codeListEntry = loadEntry(codeListName, code);
        if (codeListEntry == null) {
            return ValueHolder.of(null);
        }

        return ValueHolder.of(Tuple.create(codeListEntry.getCodeListEntryData().getTranslatedValue(language),
                                           codeListEntry.getCodeListEntryData()
                                                        .getTranslatedAdditionalValue(language)));
    }

    @Nullable
    private E loadEntry(String codeListName, String code) {
        L orCreateCodelist = findOrCreateCodelist(codeListName);
        String effectiveCode = Strings.trim(code);
        E codeListEntry = queryEntry(orCreateCodelist, effectiveCode).queryFirst();

        if (codeListEntry == null) {
            if (!orCreateCodelist.getCodeListData().isAutofill()) {
                return null;
            }
            codeListEntry = createEntry(orCreateCodelist, effectiveCode);
            codeListEntry.getMapper().update(codeListEntry);
        }
        return codeListEntry;
    }

    protected E createEntry(L codeList, String code) {
        try {
            E entry = getEntryType().getDeclaredConstructor().newInstance();
            entry.getCodeList().setValue(codeList);
            entry.getCodeListEntryData().setCode(code);
            entry.getCodeListEntryData().getValue().setFallback(code);
            return entry;
        } catch (Exception e) {
            throw Exceptions.handle(LOG, e);
        }
    }

    /**
     * Returns the value from the given code list associated with the given code.
     *
     * @param codeListName the code list to search in
     * @param code         the code to lookup
     * @return the value associated with the code or either the code itself (if {@link CodeListData#AUTO_FILL} is
     * <tt>true</tt>) or an empty optional otherwise
     */
    public Optional<String> tryGetValue(@Nonnull String codeListName, @Nullable String code) {
        if (Strings.isEmpty(code)) {
            return Optional.empty();
        }

        return tryGetValues(codeListName, code).map(Tuple::getFirst);
    }

    /**
     * Returns the value from the given code list associated with the given code in the given language.
     *
     * @param codeListName the code list to search in
     * @param code         the code to lookup
     * @param language     the language code to lookup
     * @return the value in the given language associated with the code or either the code itself
     * (if {@link CodeListData#AUTO_FILL} is <tt>true</tt>) or an empty optional otherwise
     */
    public Optional<String> tryGetTranslatedValue(@Nonnull String codeListName,
                                                  @Nullable String code,
                                                  @Nullable String language) {
        if (Strings.isEmpty(code)) {
            return Optional.empty();
        }

        if (Strings.isEmpty(language)) {
            return tryGetValue(codeListName, code);
        }

        return tryGetTranslatedValues(codeListName, code, language).map(Tuple::getFirst);
    }

    /**
     * Returns the value from the given code list associated with the given code.
     * <p>
     * If no matching entry exists, the code itself will be returned.
     *
     * @param codeListName the code list to search in
     * @param code         the code to lookup
     * @return the value associated with the code or the code itself if no value exists
     */
    @Nullable
    public String getValue(@Nonnull String codeListName, @Nullable String code) {
        return tryGetValue(codeListName, code).orElse(code);
    }

    /**
     * Returns the value from the given code list associated with the given code in the given language.
     * <p>
     * If there is no value in the given language and fallback language, the default value will be returned.
     *
     * @param codeListName the code list to search in
     * @param code         the code to lookup
     * @param language     the language code to lookup
     * @return the translated value in the given language associated with the code or the code itself if no value exists
     */
    public String getTranslatedValue(@Nonnull String codeListName, @Nullable String code, @Nullable String language) {
        return tryGetTranslatedValue(codeListName, code, language).orElse(code);
    }

    /**
     * Returns the value translated from the given code list associated with the given code for the current language.
     * <p>
     * If no matching entry exists, the code itself will be returned.
     *
     * @param codeListName the code list to search in
     * @param code         the code to lookup
     * @return the translated value associated with the code or the code itself if no value exists
     */
    @Nullable
    public String getTranslatedValue(@Nonnull String codeListName, @Nullable String code) {
        return getTranslatedValue(codeListName, code, NLS.getCurrentLanguage());
    }

    /**
     * Determines if the code list contains the given code.
     *
     * @param codeListName the code list to search in
     * @param code         the code to check
     * @return <tt>true</tt> if the code exists, <tt>false</tt> otherwise
     */
    @SuppressWarnings("squid:S2637")
    @Explain("The null check is enforced by isEmpty")
    public boolean hasValue(@Nonnull String codeListName, @Nullable String code) {
        if (Strings.isEmpty(code)) {
            return false;
        }

        return getEntry(codeListName, code).isPresent();
    }

    /**
     * Returns the value from the given code list associated with the given code or throws an exception if no matching
     * entry exists.
     *
     * @param codeListName the code list to search in
     * @param code         the code to lookup
     * @return the value associated with the code
     * @throws sirius.kernel.health.HandledException if no entry exists for the given code
     */
    public String getRequiredValue(@Nonnull String codeListName, @Nonnull String code) {
        return tryGetValue(codeListName, code).orElseThrow(() -> createMissingCodeError(codeListName, code));
    }

    protected HandledException createMissingCodeError(@Nonnull String codeListName, String code) {
        return Exceptions.handle()
                         .to(LOG)
                         .withNLSKey("CodeLists.missingEntry")
                         .set("codeList", codeListName)
                         .set("code", code)
                         .handle();
    }

    /**
     * Checks if the given code exists inside the given code list or throws an exception if no matching entry exists.
     * <p>
     * Note that if the code list cannot be resolved as no tenant is present, the check is skipped entirely. This is a
     * safety net as these are rare circumstances (etc. during login) where a check and espically a failure does more
     * harm than help. Also note that empty codes are simply ignored without reporting an error.
     *
     * @param codeListName the code list to search in
     * @param code         the code to lookup
     * @throws sirius.kernel.health.HandledException if no entry exists for the given code or code list
     */
    public void verifyValue(@Nonnull String codeListName, @Nullable String code) {
        if (Strings.isFilled(code) && getCurrentTenant(codeListName).isPresent() && tryGetValue(codeListName,
                                                                                                code).isEmpty()) {
            throw createMissingCodeError(codeListName, code);
        }
    }

    /**
     * Returns all entries of a code list.
     *
     * @param codeListName the code list to fetch entries from
     * @return a list of all avilable entries in the given code list, sorted by priority
     */
    @NoodleSandbox(NoodleSandbox.Accessibility.GRANTED)
    public List<E> getEntries(@Nonnull String codeListName) {
        L codeList = findOrCreateCodelist(codeListName);
        return createEntryQuery().eq(CodeListEntry.CODE_LIST, codeList)
                                 .orderAsc(CodeListEntry.CODE_LIST_ENTRY_DATA.inner(CodeListEntryData.PRIORITY))
                                 .orderAsc(CodeListEntry.CODE_LIST_ENTRY_DATA.inner(CodeListEntryData.CODE))
                                 .queryList();
    }

    /**
     * Returns the {@link CodeListEntry} from the given code list associated with the given code.
     *
     * @param codeListName the code list to search in
     * @param code         the code to lookup
     * @return the entry associated with the code or an empty optional otherwise
     */
    public Optional<E> getEntry(@Nonnull String codeListName, String code) {
        L codeList = findOrCreateCodelist(codeListName);
        return queryEntry(codeList, code).first();
    }

    /**
     * Completely clears the cache holding all known values.
     */
    public void clearCache() {
        valueCache.clear();
        CodeListLookupTable.flushReverseLookupCache();
    }
}
