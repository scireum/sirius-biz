/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho;

import sirius.biz.tenants.UserAccount;
import sirius.web.http.WebContext;
import sirius.web.security.UserContext;

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
     * This is either controlled by a <tt>display-mode</tt> parameter (if the user actively changed the display mode) or by the
     * given user preference (<tt>userPreferencesKey</tt>) to keep the display mode consistent across sessions.
     *
     * @param webContext         the current request
     * @param userPreferencesKey the user preference key to read
     * @return the {@link DisplayMode} to use
     */
    public static DisplayMode determineLayout(WebContext webContext, String userPreferencesKey) {
        UserAccount<?, ?> user = UserContext.getCurrentUser().as(UserAccount.class);
        if (webContext.hasParameter(PARAM_DISPLAY_MODE)) {
            DisplayMode displayMode =
                    webContext.get(PARAM_DISPLAY_MODE).getEnum(DisplayMode.class).orElse(DEFAULT_DISPLAY_MODE);
            if (displayMode == DEFAULT_DISPLAY_MODE) {
                user.updatePreference(userPreferencesKey, null);
            } else {
                user.updatePreference(userPreferencesKey, displayMode.name());
            }

            return displayMode;
        }

        return user.readPreference(userPreferencesKey).getEnum(DisplayMode.class).orElse(DEFAULT_DISPLAY_MODE);
    }
}
