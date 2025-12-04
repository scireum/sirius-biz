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

    /**
     * The default page size used if no preference is set.
     */
    public static final PageSize DEFAULT_PAGE_SIZE = PageSize.SIZE_25;

    /**
     * The request parameter used to change the page size.
     */
    public static final String PARAM_PAGE_SIZE = "page-size";

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
        return determinePageSize(webContext, userPreferencesKey, ContentListLayout.DisplayMode.LIST);
    }

    /**
     * Determines the page size to use for the given user with a custom default value.
     * <p>
     * The mode is either controlled by a <tt>page-size</tt> parameter (if the user actively changed the page size)
     * or by the given user preference (<tt>userPreferencesKey</tt>) to keep the page size consistent across sessions.
     * If no preference is set, the default value is used and the display mode list is used.
     *
     * @param webContext         the current request
     * @param userPreferencesKey the user preference key to read
     * @param displayMode        the display mode which is used to show the content
     * @return the page size to use
     */
    @SuppressWarnings("unchecked")
    @Explain("tryAs raises a generics cast error in this case.")
    public static int determinePageSize(WebContext webContext,
                                        String userPreferencesKey,
                                        ContentListLayout.DisplayMode displayMode) {
        Optional<UserAccount<?, ?>> user =
                UserContext.getCurrentUser().tryAs((Class<UserAccount<?, ?>>) (Class<?>) UserAccount.class);
        if (webContext.hasParameter(PARAM_PAGE_SIZE)) {
            PageSize pageSize = PageSize.getPageSizeFor(webContext.get(PARAM_PAGE_SIZE).asOptionalInt());
            updateUsersPreference(user, userPreferencesKey, pageSize);
            return getPageSizeForLayout(pageSize, displayMode);
        }
        return determinePageSizeByUsersPreference(user, userPreferencesKey, displayMode);
    }

    private static void updateUsersPreference(Optional<UserAccount<?, ?>> user,
                                              String userPreferencesKey,
                                              PageSize pageSize) {
        user.ifPresent(userAccount -> {
            if (pageSize == DEFAULT_PAGE_SIZE) {
                userAccount.updatePreference(userPreferencesKey, null);
            } else {
                userAccount.updatePreference(userPreferencesKey, pageSize.getSizeTable());
            }
        });
    }

    private static int determinePageSizeByUsersPreference(Optional<UserAccount<?, ?>> user,
                                                          String userPreferencesKey,
                                                          ContentListLayout.DisplayMode displayMode) {
        return getPageSizeForLayout(PageSize.getPageSizeFor(user.flatMap(userAccount -> userAccount.readPreference(
                userPreferencesKey).asOptionalInt())), displayMode);
    }

    private static Integer getPageSizeForLayout(PageSize pageSize, ContentListLayout.DisplayMode displayMode) {
        return pageSize.getSize(displayMode);
    }
}
