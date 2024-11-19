/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.password;

import sirius.db.mixing.Entity;
import sirius.kernel.Sirius;
import sirius.kernel.di.std.Register;
import sirius.kernel.settings.ExtendedSettings;
import sirius.kernel.settings.Settings;

/**
 * Provides the settings for password generation and validation.
 * <p>
 * The class may be {@linkplain sirius.kernel.di.Replace replaced} to provide custom settings based on the given entity.
 */
@Register(classes = PasswordSettings.class)
public class PasswordSettings {

    public static final String SETTINGS_GENERATED = "security.passwords.generated";
    public static final String SETTINGS_USER_BASE = "security.passwords.user";

    public static final String SETTINGS_CATEGORIES = "categories";
    public static final String SETTINGS_CATEGORY_INSUFFICIENT = "insufficient";
    public static final String SETTINGS_CATEGORY_WEAK = "weak";
    public static final String SETTINGS_CATEGORY_FINE = "fine";
    public static final String SETTINGS_CATEGORY_SECURE = "secure";

    public static final String SETTING_GENERATED_LENGTH = "length";
    public static final String SETTING_GENERATED_CONSIDERED_CHARACTERS = "consideredCharacters";

    public static final String SETTING_USER_MIN_LENGTH = "minLength";
    public static final String SETTING_USER_REQUIRE_LETTERS_AND_DIGITS = "requireLettersAndDigits";
    public static final String SETTING_USER_REQUIRE_UPPER_AND_LOWER_CASE = "requireUpperAndLowerCase";
    public static final String SETTING_USER_REQUIRE_SPECIAL_CHARACTERS = "requireSpecialCharacters";

    /**
     * Resolves the settings for generated passwords that apply to the given entity.
     *
     * @param entity the entity to resolve the settings for
     * @return the resolved settings
     */
    public Settings resolveGeneratedPasswordSettings(Entity entity) {
        return Sirius.getSettings().getSettings(SETTINGS_GENERATED);
    }

    /**
     * Resolves the settings for user generated passwords that apply to the given entity.
     *
     * @param entity the entity to resolve the settings for
     * @return the resolved settings
     */
    public ExtendedSettings resolveUserPasswordSettings(Entity entity) {
        return new ExtendedSettings(Sirius.getSettings().getConfig(SETTINGS_USER_BASE), true);
    }
}
