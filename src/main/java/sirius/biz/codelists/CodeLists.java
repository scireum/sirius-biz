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
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.kernel.settings.Extension;
import sirius.web.security.ScopeInfo;
import sirius.web.security.UserContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

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

    protected Cache<String, String> valueCache = CacheManager.createCoherentCache("codelists-values");

    protected static final Log LOG = Log.get("codelists");

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
     * Returns the value from the given code list associated with the given code.
     * <p>
     * If no matching entry exists, the code itself will be returned.
     *
     * @param codeList the code list to search in
     * @param code     the code to lookup
     * @return the value associated with the code or the code itself if no value exists
     */
    @Nullable
    @SuppressWarnings("squid:S2637")
    @Explain("code cannot be null due to Strings.isEmpty")
    public String getValue(@Nonnull String codeList, @Nullable String code) {
        if (Strings.isEmpty(code)) {
            return null;
        }

        Tenant<?> tenant = getCurrentTenant();

        return valueCache.get(tenant.getIdAsString() + codeList + "|" + code, ignored -> {
            return getValues(codeList, code).getFirst();
        });
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

        L cl = findOrCreateCodelist(codeList);
        return queryEntry(cl, code).exists();
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
        L cl = findOrCreateCodelist(codeList);
        E cle = queryEntry(cl, code).queryFirst();

        if (cle == null) {
            throw Exceptions.handle()
                            .to(LOG)
                            .withSystemErrorMessage("Unable to find required code ('%s') in code list ('%s')",
                                                    code,
                                                    codeList)
                            .handle();
        }

        return cle.getCodeListEntryData().getValue();
    }

    protected Query<?, E, ?> queryEntry(L list, @Nonnull String code) {
        return createEntryQuery().eq(SQLCodeListEntry.CODE_LIST, list)
                                 .eq(SQLCodeListEntry.CODE_LIST_ENTRY_DATA.inner(CodeListEntryData.CODE), code);
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
        L cl = findOrCreateCodelist(codeList);
        E cle = queryEntry(cl, code).queryFirst();

        if (cle == null) {
            if (!cl.getCodeListData().isAutofill()) {
                return Tuple.create(code, null);
            }
            cle = createEntry(cl, code);
            cle.getMapper().update(cle);
        }

        return Tuple.create(cle.getCodeListEntryData().getValue(), cle.getCodeListEntryData().getAdditionalValue());
    }

    protected E createEntry(L cl, String code) {
        try {
            E entry = getEntryType().getDeclaredConstructor().newInstance();
            entry.getCodeList().setValue(cl);
            entry.getCodeListEntryData().setCode(code);
            return entry;
        } catch (Exception e) {
            throw Exceptions.handle(LOG, e);
        }
    }

    private L findOrCreateCodelist(@Nonnull String codeList) {
        if (Strings.isEmpty(codeList)) {
            throw new IllegalArgumentException("codeList must not be empty");
        }

        L cl = createListQuery().eq(CodeList.TENANT, getCurrentTenant())
                                .eq(CodeList.CODE_LIST_DATA.inner(CodeListData.CODE), codeList)
                                .queryFirst();
        if (cl == null) {
            Extension ext = Sirius.getSettings().getExtension("code-lists", codeList);
            cl = createCodeList(getCurrentTenant(), codeList);
            if (ext != null && !ext.isDefault()) {
                cl.getCodeListData().setName(ext.get("name").asString());
                cl.getCodeListData().setDescription(ext.get("description").asString());
                cl.getCodeListData().setAutofill(ext.get("autofill").asBoolean());
            }

            cl.getMapper().update(cl);
        }

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
     * Determines the effective current tenant.
     * <p>
     * This also gracefully handles scopes other than the current tenant as long as there is an adapter to make it
     * match a {@link CodeListTenantProvider}.
     *
     * @return the current tenant to be used to determine which code lists to operate on.
     */
    private Tenant<?> getCurrentTenant() {
        if (UserContext.getCurrentScope() == ScopeInfo.DEFAULT_SCOPE) {
            return tenants.getRequiredTenant();
        }

        Tenant<?> currentTenant = UserContext.getCurrentScope().as(CodeListTenantProvider.class).getCurrentTenant();

        if (currentTenant == null) {
            throw Exceptions.handle()
                            .to(LOG)
                            .withSystemErrorMessage("Cannot determine the effective tenant for scope %!",
                                                    UserContext.getCurrentScope().getScopeId())
                            .handle();
        }

        return currentTenant;
    }

    /**
     * Completely clears the cache holding all known values.
     */
    public void clearCache() {
        valueCache.clear();
    }
}
