/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import sirius.biz.tenants.UserAccount;
import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Register;
import sirius.web.controller.Page;
import sirius.web.http.WebContext;
import sirius.web.security.UserContext;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Provides common request parameters and the currently selected values for table sorting.
 */
@Register(classes = TableSorting.class)
public class TableSorting {

    public static final String PARAM_SORT = "sort";
    public static final String PARAM_ORDER = "order";
    public static final String PARAM_CLEAR_SORT = "clear-sort";
    public static final String ATTRIBUTE_SORT_OPTIONS = "tableSortOptions";

    public static final String ORDER_ASC = SortOrder.ASC.getParameterValue();
    public static final String ORDER_DESC = SortOrder.DESC.getParameterValue();

    private static final String SORT_PREFERENCE_SUFFIX = ".sort";
    private static final String ORDER_PREFERENCE_SUFFIX = ".order";

    /**
     * Returns the currently requested sort key or an empty string if none is active.
     *
     * @return the currently requested sort key
     */
    public String fetchCurrentSortKey() {
        return fetchCurrentSortKey(WebContext.getCurrent());
    }

    /**
     * Returns the effective sort key for the current request.
     * <p>
     * If a complete sort selection is present in the request, this is used. Otherwise, a persisted user preference
     * for the given page key is used if present.
     *
     * @param userPreferencesKey the page-specific preference key to inspect
     * @return the effective sort key or an empty string if none is active
     */
    public String fetchCurrentSortKey(String userPreferencesKey) {
        return fetchCurrentSortKey(WebContext.getCurrent(), userPreferencesKey);
    }

    /**
     * Returns the currently requested sort key or an empty string if none is active.
     *
     * @param webContext the request to inspect
     * @return the currently requested sort key
     */
    public String fetchCurrentSortKey(WebContext webContext) {
        return webContext.get(PARAM_SORT).asString();
    }

    /**
     * Returns the effective sort key for the given request.
     * <p>
     * If a complete sort selection is present in the request, this is used. Otherwise, a persisted user preference
     * for the given page key is used if present.
     *
     * @param webContext         the request to inspect
     * @param userPreferencesKey the page-specific preference key to inspect
     * @return the effective sort key or an empty string if none is active
     */
    public String fetchCurrentSortKey(WebContext webContext, String userPreferencesKey) {
        return resolveCurrentSorting(webContext, userPreferencesKey).sortKey;
    }

    /**
     * Returns the currently requested order or an empty string if none is valid.
     *
     * @return the currently requested order
     */
    public String fetchCurrentOrderValue() {
        return fetchCurrentOrderValue(WebContext.getCurrent());
    }

    /**
     * Returns the effective order value for the current request.
     * <p>
     * If a complete sort selection is present in the request, this is used. Otherwise, a persisted user preference
     * for the given page key is used if present.
     *
     * @param userPreferencesKey the page-specific preference key to inspect
     * @return the effective order value or an empty string if none is active
     */
    public String fetchCurrentOrderValue(String userPreferencesKey) {
        return fetchCurrentOrderValue(WebContext.getCurrent(), userPreferencesKey);
    }

    /**
     * Returns the currently requested order or an empty string if none is valid.
     *
     * @param webContext the request to inspect
     * @return the currently requested order
     */
    public String fetchCurrentOrderValue(WebContext webContext) {
        return normalizeOrderValue(webContext.get(PARAM_ORDER).asString());
    }

    /**
     * Returns the effective order value for the given request.
     * <p>
     * If a complete sort selection is present in the request, this is used. Otherwise, a persisted user preference
     * for the given page key is used if present.
     *
     * @param webContext         the request to inspect
     * @param userPreferencesKey the page-specific preference key to inspect
     * @return the effective order value or an empty string if none is active
     */
    public String fetchCurrentOrderValue(WebContext webContext, String userPreferencesKey) {
        return resolveCurrentSorting(webContext, userPreferencesKey).orderValue;
    }

    public static String readStoredSortKey(String userPreferencesKey) {
        if (Strings.isEmpty(userPreferencesKey)) {
            return "";
        }

        return readStoredPreference(sortPreferenceKey(userPreferencesKey));
    }

    public static String readStoredOrderValue(String userPreferencesKey) {
        if (Strings.isEmpty(userPreferencesKey)) {
            return "";
        }

        return normalizeOrderValue(readStoredPreference(orderPreferenceKey(userPreferencesKey)));
    }

    public static void storeSortingPreference(String userPreferencesKey, String sortKey, SortOrder sortOrder) {
        if (Strings.isEmpty(userPreferencesKey) || Strings.isEmpty(sortKey) || sortOrder == null) {
            return;
        }

        fetchCurrentUserAccount().ifPresent(user -> {
            user.updatePreference(sortPreferenceKey(userPreferencesKey), sortKey);
            user.updatePreference(orderPreferenceKey(userPreferencesKey), sortOrder.getParameterValue());
        });
    }

    public static void clearSortingPreference(String userPreferencesKey) {
        if (Strings.isEmpty(userPreferencesKey)) {
            return;
        }

        fetchCurrentUserAccount().ifPresent(user -> {
            user.updatePreference(sortPreferenceKey(userPreferencesKey), null);
            user.updatePreference(orderPreferenceKey(userPreferencesKey), null);
        });
    }

    /**
     * Fetches the sortable options attached to the given page.
     *
     * @param page the page to fetch the options from
     * @return the sortable options attached to the page or an empty list if none were attached
     */
    @SuppressWarnings("unchecked")
    public List<TableSortOption> fetchSortOptions(Page<?> page) {
        Object sortOptions = page.getAttribute(ATTRIBUTE_SORT_OPTIONS);

        if (sortOptions instanceof List<?>) {
            return (List<TableSortOption>) sortOptions;
        }

        return Collections.emptyList();
    }

    private static String sortPreferenceKey(String userPreferencesKey) {
        return userPreferencesKey + SORT_PREFERENCE_SUFFIX;
    }

    private static String orderPreferenceKey(String userPreferencesKey) {
        return userPreferencesKey + ORDER_PREFERENCE_SUFFIX;
    }

    private static String normalizeOrderValue(String orderValue) {
        SortOrder sortOrder = SortOrder.fromParameter(orderValue);
        return sortOrder == null ? "" : sortOrder.getParameterValue();
    }

    private SortingState resolveCurrentSorting(WebContext webContext, String userPreferencesKey) {
        if (webContext.get(PARAM_CLEAR_SORT).asBoolean()) {
            return SortingState.empty();
        }

        String requestedSortKey = fetchCurrentSortKey(webContext);
        String requestedOrderValue = fetchCurrentOrderValue(webContext);

        if (Strings.isFilled(requestedSortKey) && Strings.isFilled(requestedOrderValue)) {
            return new SortingState(requestedSortKey, requestedOrderValue);
        }

        if (Strings.isEmpty(userPreferencesKey)) {
            return new SortingState(requestedSortKey, requestedOrderValue);
        }

        return new SortingState(readStoredSortKey(userPreferencesKey), readStoredOrderValue(userPreferencesKey));
    }

    private static String readStoredPreference(String preferenceKey) {
        return fetchCurrentUserAccount().map(user -> user.readPreference(preferenceKey).asString()).orElse("");
    }

    @SuppressWarnings("unchecked")
    @Explain("tryAs raises a generics cast error in this case.")
    private static Optional<UserAccount<?, ?>> fetchCurrentUserAccount() {
        return UserContext.getCurrentUser().tryAs((Class<UserAccount<?, ?>>) (Class<?>) UserAccount.class);
    }

    private static final class SortingState {
        private final String sortKey;
        private final String orderValue;

        private SortingState(String sortKey, String orderValue) {
            this.sortKey = sortKey;
            this.orderValue = orderValue;
        }

        private static SortingState empty() {
            return new SortingState("", "");
        }
    }
}

