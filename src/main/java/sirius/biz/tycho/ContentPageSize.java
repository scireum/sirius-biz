/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho;

import sirius.biz.tenants.UserAccount;
import sirius.kernel.commons.Explain;
import sirius.web.http.WebContext;
import sirius.web.security.UserContext;

import java.util.Optional;

/**
 * Contains the logic for the page size for lists and handles the user preference.
 */
public class ContentPageSize {

    private ContentPageSize() {
    }

    private static final int DEFAULT_PAGE_SIZE = 25;
    private static final String PARAM_PAGE_SIZE = "page-size";

    /**
     * Determines the page size to use for the given user.
     * <p>
     * The page size is either controlled by a <tt>page-size</tt> parameter (if the user actively changed the page size)
     * or by the given user preference (<tt>userPreferencesKey</tt>) to keep the page size consistent across sessions.
     * If no preference is set, the default value is used and the display mode list is used.
     *
     * @param webContext         the current request
     * @param userPreferencesKey the user preference key to read
     * @return the page size to use
     */
    public static int determinePageSize(WebContext webContext, String userPreferencesKey) {
        return determinePageSize(webContext, userPreferencesKey, DEFAULT_PAGE_SIZE, ContentListLayout.DisplayMode.LIST);
    }

    /**
     * Determines the page size to use for the given user.
     * <p>
     * The page size is either controlled by a <tt>page-size</tt> parameter (if the user actively changed the page size)
     * or by the given user preference (<tt>userPreferencesKey</tt>) to keep the page size consistent across sessions.
     * If no preference is set, the default value is used.
     *
     * @param webContext         the current request
     * @param userPreferencesKey the user preference key to read
     * @return the page size to use
     */
    public static int determinePageSize(WebContext webContext,
                                        String userPreferencesKey,
                                        ContentListLayout.DisplayMode displayMode) {
        return determinePageSize(webContext, userPreferencesKey, DEFAULT_PAGE_SIZE, displayMode);
    }

    /**
     * Determines the page size to use for the given user with a custom default value.
     * <p>
     * The mode is either controlled by a <tt>page-size</tt> parameter (if the user actively changed the page size)
     * or by the given user preference (<tt>userPreferencesKey</tt>) to keep the page size consistent across sessions.
     * If no preference is set, the provided default value is used.
     *
     * @param webContext         the current request
     * @param userPreferencesKey the user preference key to read
     * @param defaultPageSize    the default page size if no preference is set
     * @param displayMode        the display mode which is used to show the content
     * @return the page size to use
     */
    @SuppressWarnings("unchecked")
    @Explain("tryAs raises a generics cast error in this case.")
    public static int determinePageSize(WebContext webContext,
                                        String userPreferencesKey,
                                        int defaultPageSize,
                                        ContentListLayout.DisplayMode displayMode) {
        Optional<UserAccount<?, ?>> user =
                UserContext.getCurrentUser().tryAs((Class<UserAccount<?, ?>>) (Class<?>) UserAccount.class);
        if (webContext.hasParameter(PARAM_PAGE_SIZE)) {
            int pageSize = webContext.get(PARAM_PAGE_SIZE).asInt(defaultPageSize);
            if (pageSize != 25 && pageSize != 50 && pageSize != 100) {
                // Fallback to default if the provided page size is invalid
                pageSize = defaultPageSize;
            }
            updateUsersPreference(user, userPreferencesKey, pageSize, defaultPageSize);
            return getPageSizeForLayout(pageSize, displayMode);
        }
        return determinePageSizeByUsersPreference(user, userPreferencesKey, displayMode);
    }

    private static void updateUsersPreference(Optional<UserAccount<?, ?>> user,
                                              String userPreferencesKey,
                                              int pageSize,
                                              int defaultPageSize) {
        user.ifPresent(userAccount -> {
            if (pageSize == defaultPageSize) {
                userAccount.updatePreference(userPreferencesKey, null);
            } else {
                userAccount.updatePreference(userPreferencesKey, pageSize);
            }
        });
    }

    private static int determinePageSizeByUsersPreference(Optional<UserAccount<?, ?>> user,
                                                          String userPreferencesKey,
                                                          ContentListLayout.DisplayMode displayMode) {
        return getPageSizeForLayout(user.flatMap(userAccount -> userAccount.readPreference(userPreferencesKey)
                                                                           .asOptionalInt()).orElse(DEFAULT_PAGE_SIZE),
                                    displayMode);
    }

    private static Integer getPageSizeForLayout(int pageSize, ContentListLayout.DisplayMode displayMode) {
        if (ContentListLayout.DisplayMode.CARDS == displayMode) {
            return switch (pageSize) {
                case 50 -> 48;
                case 100 -> 96;
                default -> 24;
            };
        }
        return pageSize;
    }
}
