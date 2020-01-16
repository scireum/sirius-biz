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
import sirius.kernel.commons.Value;
import sirius.kernel.commons.ValueHolder;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.kernel.health.Log;
import sirius.kernel.settings.Extension;
import sirius.web.security.ScopeInfo;
import sirius.web.security.UserContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
public abstract class CodeLists<I, L extends BaseEntity<I> & CodeList, E extends BaseEntity<I> & CodeListEntry<I, L>> {

    protected static final String CONFIG_EXTENSION_CODE_LISTS = "code-lists";
    protected static final String CONFIG_KEY_NAME = "name";
    protected static final String CONFIG_KEY_DESCRIPTION = "description";
    protected static final String CONFIG_KEY_AUTOFILL = "autofill";
    protected static final String CONFIG_KEY_GLOBAL = "global";
    protected Cache<String, ValueHolder<Tuple<String, String>>> valueCache =
            CacheManager.createCoherentCache("codelists-values");

    protected static final Log LOG = Log.get("codelists");

    protected Map<String, Boolean> codeListGlobalFlag = new HashMap<>();

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
     * @param codeList The code of the {@link CodeList}
     * @return An optional of the matching {@link CodeList}, or null if there is no
     * codeList with the given name
     */
    public Optional<L> findCodelist(@Nonnull String codeList) {
        if (Strings.isEmpty(codeList)) {
            throw new IllegalArgumentException("codeList must not be empty");
        }

        Tenant<?> currentTenant = getCurrentTenant(codeList).orElseThrow(this::warnAboutMissingTenant);

        L cl = createListQuery().eq(CodeList.TENANT, currentTenant)
                                .eq(CodeList.CODE_LIST_DATA.inner(CodeListData.CODE), codeList)
                                .queryFirst();

        return Optional.ofNullable(cl);
    }

    protected HandledException warnAboutMissingTenant() {
        return Exceptions.handle().to(LOG).withNLSKey("CodeLists.noCurrentTenant").handle();
    }

    private L findOrCreateCodelist(@Nonnull String codeList) {
        Optional<L> optionalCodeList = findCodelist(codeList);

        Tenant<?> currentTenant = getCurrentTenant(codeList).orElseThrow(this::warnAboutMissingTenant);

        if (optionalCodeList.isPresent()) {
            return optionalCodeList.get();
        }
        Extension ext = getCodeListConfig(codeList);

        L cl = createCodeList(currentTenant, codeList);
        if (ext != null && !ext.isDefault()) {
            cl.getCodeListData().setName(ext.get(CONFIG_KEY_NAME).asString());
            cl.getCodeListData().setDescription(ext.get(CONFIG_KEY_DESCRIPTION).asString());
            cl.getCodeListData().setAutofill(ext.get(CONFIG_KEY_AUTOFILL).asBoolean());
        }

        cl.getMapper().update(cl);

        return cl;
    }

    private L createCodeList(@Nonnull Tenant<?> tenant, String codeList) {
        try {
            L list = getListType().getDeclaredConstructor().newInstance();
            list.withTenant(tenant);
            list.getCodeListData().setCode(codeList);
            list.getCodeListData().setName(codeList);
            return list;
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
     * @return optional of the current tenant to be used to determine which code lists to operate on.
     */
    @SuppressWarnings("unchecked")
    private Optional<Tenant<?>> getCurrentTenant(String codeList) {
        if (isCodeListGlobal(codeList)) {
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

    private boolean isCodeListGlobal(String codeList) {
        return codeListGlobalFlag.computeIfAbsent(codeList, ignored -> {
            Extension ext = getCodeListConfig(codeList);
            boolean isGlobal = ext.get(CONFIG_KEY_GLOBAL).asBoolean();
            if (isGlobal && ext.get(CONFIG_KEY_AUTOFILL).asBoolean()) {
                LOG.WARN("The code list %s is 'global' and has 'autofill' enabled. This is a dangerous combination!");
            }
            return isGlobal;
        });
    }

    private Extension getCodeListConfig(String codeList) {
        return Sirius.getSettings().getExtension(CONFIG_EXTENSION_CODE_LISTS, codeList);
    }

    /**
     * Returns the value and the additionalValue associated with the given code.
     *
     * @param codeList the code list to search in
     * @param code     the code to lookup
     * @return the value and the additional value associated with the given code or the code itself and <tt>null</tt>
     * (if {@link CodeListData#AUTO_FILL} is <tt>true</tt>) or an empty optional otherwise
     */
    public Optional<Tuple<String, String>> tryGetValues(@Nonnull String codeList, @Nullable String code) {
        if (Strings.isEmpty(code)) {
            return Optional.empty();
        }
        return getCurrentTenant(codeList).flatMap(tenant -> fetchValueFromCache(tenant, codeList, code));
    }

    private Optional<Tuple<String, String>> fetchValueFromCache(Tenant<?> tenant, String codeList, String code) {
        return valueCache.get(tenant.getIdAsString() + codeList + "|" + code, ignored -> loadValues(codeList, code))
                         .asOptional();
    }

    private ValueHolder<Tuple<String, String>> loadValues(String codeList, String code) {
        L cl = findOrCreateCodelist(codeList);
        String effectiveCode = Strings.trim(code);
        E cle = queryEntry(cl, effectiveCode).queryFirst();

        if (cle == null) {
            if (!cl.getCodeListData().isAutofill()) {
                return ValueHolder.of(null);
            }
            cle = createEntry(cl, effectiveCode);
            cle.getMapper().update(cle);
        }

        return ValueHolder.of(Tuple.create(cle.getCodeListEntryData().getValue(),
                                           cle.getCodeListEntryData().getAdditionalValue()));
    }

    /**
     * Returns the value and the additionalValue associated with the given code.
     * <p>
     * If no matching entry exists, the code itself will be returned as value and the additional value (the second of
     * the tuple) will be <tt>null</tt>.
     *
     * @param codeList the code list to search in
     * @param code     the code to lookup
     * @return the value and the additional value associated with the given code, wrapped as tuple
     */
    public Tuple<String, String> getValues(@Nonnull String codeList, @Nonnull String code) {
        return tryGetValues(codeList, code).orElseGet(() -> Tuple.create(code, null));
    }

    protected E createEntry(L cl, String code) {
        try {
            E entry = getEntryType().getDeclaredConstructor().newInstance();
            entry.getCodeList().setValue(cl);
            entry.getCodeListEntryData().setCode(code);
            entry.getCodeListEntryData().setValue(code);
            return entry;
        } catch (Exception e) {
            throw Exceptions.handle(LOG, e);
        }
    }

    /**
     * Returns the value from the given code list associated with the given code.
     *
     * @param codeList the code list to search in
     * @param code     the code to lookup
     * @return the value associated with the code or either the code itself (if {@link CodeListData#AUTO_FILL} is
     * <tt>true</tt>) or an empty optional otherwise
     */
    public Optional<String> tryGetValue(@Nonnull String codeList, @Nullable String code) {
        if (Strings.isEmpty(code)) {
            return Optional.empty();
        }

        return tryGetValues(codeList, code).map(Tuple::getFirst);
    }

    /**
     * Returns the value from the given code list associated with the given code.
     * <p>
     * If no matching entry exists, the code itself will be returned.
     *
     * @param codeList the code list to search in
     * @param code     the code to lookup
     * @return the value associated with the code or the code itself if no value exists
     */
    @Nullable
    public String getValue(@Nonnull String codeList, @Nullable String code) {
        return tryGetValue(codeList, code).orElse(code);
    }

    /**
     * Returns the value translated from the given code list associated with the given code.
     * <p>
     * If no matching entry exists, the code itself will be returned.
     *
     * @param codeList the code list to search in
     * @param code     the code to lookup
     * @return the translated value associated with the code or the code itself if no value exists
     */
    @Nullable
    public String getTranslatedValue(@Nonnull String codeList, @Nullable String code) {
        return Value.of(getValue(codeList, code)).translate().getString();
    }

    /**
     * Determines if the code list contains the given code.
     *
     * @param codeList the code list to search in
     * @param code     the code to check
     * @return <tt>true</tt> if the code exists, <tt>false</tt> otherwise
     */
    @SuppressWarnings("squid:S2637")
    @Explain("The null check is enforced by isEmpty")
    public boolean hasValue(@Nonnull String codeList, @Nullable String code) {
        if (Strings.isEmpty(code)) {
            return false;
        }

        return tryGetValue(codeList, code).isPresent();
    }

    /**
     * Returns the value from the given code list associated with the given code or throws an exception if no matching
     * entry exists.
     *
     * @param codeList the code list to search in
     * @param code     the code to lookup
     * @return the value associated with the code
     * @throws sirius.kernel.health.HandledException if no entry exists for the given code
     */
    public String getRequiredValue(@Nonnull String codeList, @Nonnull String code) {
        return tryGetValue(codeList, code).orElseThrow(() -> createMissingCodeError(codeList, code));
    }

    protected HandledException createMissingCodeError(@Nonnull String codeList, String code) {
        return Exceptions.handle()
                         .to(LOG)
                         .withNLSKey("CodeLists.missingEntry")
                         .set("codeList", codeList)
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
     * @param codeList the code list to search in
     * @param code     the code to lookup
     * @throws sirius.kernel.health.HandledException if no entry exists for the given code or code list
     */
    public void verifyValue(@Nonnull String codeList, @Nullable String code) {
        if (Strings.isFilled(code) && getCurrentTenant(codeList).isPresent() && !tryGetValue(codeList,
                                                                                             code).isPresent()) {
            throw createMissingCodeError(codeList, code);
        }
    }

    /**
     * Returns all entries of a code list.
     *
     * @param codeList the code list to fetch entries from
     * @return a list of all avilable entries in the given code list, sorted by priority
     */
    public List<E> getEntries(@Nonnull String codeList) {
        L cl = findOrCreateCodelist(codeList);
        return createEntryQuery().eq(CodeListEntry.CODE_LIST, cl)
                                 .orderAsc(CodeListEntry.CODE_LIST_ENTRY_DATA.inner(CodeListEntryData.PRIORITY))
                                 .orderAsc(CodeListEntry.CODE_LIST_ENTRY_DATA.inner(CodeListEntryData.CODE))
                                 .queryList();
    }

    /**
     * Returns the {@link CodeListEntry} from the given code list associated with the given code.
     *
     * @param codeList the code list to search in
     * @param code     the code to lookup
     * @return the entry associated with the code or an empty optional otherwise
     */
    public Optional<E> getEntry(@Nonnull String codeList, String code) {
        L cl = findOrCreateCodelist(codeList);
        return queryEntry(cl, code).first();
    }

    /**
     * Completely clears the cache holding all known values.
     */
    public void clearCache() {
        valueCache.clear();
    }
}
