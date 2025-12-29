/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
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
 * Contains the {@link DisplayMode} options for lists and handles the user preference.
 */
public class ContentListLayout {
    /**
     * Contains the possible display modes for a list.
     */
    public enum DisplayMode {
        /**
         * Displays the list as a grid of cards.
         */
        CARDS,

        /**
         * Displays the list as a simple list.
         */
        LIST
    }

    private ContentListLayout() {
    }

    private static final DisplayMode DEFAULT_DISPLAY_MODE = DisplayMode.CARDS;
    private static final String PARAM_DISPLAY_MODE = "display-mode";

    /**
     * Determines the {@link DisplayMode} to use for the given user.
     * <p>
     * The mode is either controlled by a <tt>display-mode</tt> parameter (if the user actively changed the display mode)
     * or by the given user preference (<tt>userPreferencesKey</tt>) to keep the display mode consistent across sessions.
     * If no preference is set, the default value is used.
     *
     * @param webContext         the current request
     * @param userPreferencesKey the user preference key to read
     * @return the {@link DisplayMode} to use
     */
    public static DisplayMode determineLayout(WebContext webContext, String userPreferencesKey) {
        return determineLayout(webContext, userPreferencesKey, DEFAULT_DISPLAY_MODE);
    }

    /**
     * Determines the {@link DisplayMode} to use for the given user with a custom default value.
     * <p>
     * The mode is either controlled by a <tt>display-mode</tt> parameter (if the user actively changed the display mode)
     * or by the given user preference (<tt>userPreferencesKey</tt>) to keep the display mode consistent across sessions.
     * If no preference is set, the provided default value is used.
     *
     * @param webContext         the current request
     * @param userPreferencesKey the user preference key to read
     * @param defaultDisplayMode the default display mode if no preference is set
     * @return the {@link DisplayMode} to use
     */
    @SuppressWarnings("unchecked")
    @Explain("tryAs raises a generics cast error in this case.")
    public static DisplayMode determineLayout(WebContext webContext, String userPreferencesKey, DisplayMode defaultDisplayMode) {
        Optional<UserAccount<?, ?>> user =
                UserContext.getCurrentUser().tryAs((Class<UserAccount<?, ?>>) (Class<?>) UserAccount.class);
        if (webContext.hasParameter(PARAM_DISPLAY_MODE)) {
            DisplayMode displayMode =
                    webContext.get(PARAM_DISPLAY_MODE).getEnum(DisplayMode.class).orElse(defaultDisplayMode);
            updateUsersPreference(user, userPreferencesKey, displayMode, defaultDisplayMode);
            return displayMode;
        }
        return determineLayoutByUsersPreference(user, userPreferencesKey, defaultDisplayMode);
    }

    private static void updateUsersPreference(Optional<UserAccount<?, ?>> user,
                                              String userPreferencesKey,
                                              DisplayMode displayMode,
                                              DisplayMode defaultDisplayMode) {
        user.ifPresent(userAccount -> {
            if (displayMode == defaultDisplayMode) {
                userAccount.updatePreference(userPreferencesKey, null);
            } else {
                userAccount.updatePreference(userPreferencesKey, displayMode.name());
            }
        });
    }

    private static DisplayMode determineLayoutByUsersPreference(Optional<UserAccount<?, ?>> user,
                                                                String userPreferencesKey,
                                                                DisplayMode defaultDisplayMode) {
        return user.flatMap(userAccount -> userAccount.readPreference(userPreferencesKey).getEnum(DisplayMode.class))
                   .orElse(defaultDisplayMode);
    }
}
