/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.password;

import sirius.db.mixing.Entity;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.settings.Settings;

/**
 * Validates user defined passwords.
 */
@Register(classes = PasswordValidator.class)
public class PasswordValidator {

    @Part
    private PasswordSettings passwordSettings;

    /**
     * Validates a password based on the configured rules that apply to the given entity.
     *
     * @param entity   the entity to validate the password for
     * @param password the password to validate
     * @return <tt>true</tt> if the password meets at least the "weak" requirements, <tt>false</tt> otherwise
     */
    public boolean isPasswordValid(Entity entity, String password) {
        if (Strings.isEmpty(password)) {
            return false;
        }

        Settings requirements = passwordSettings.resolveUserPasswordSettings(entity)
                                                .getExtension(PasswordSettings.SETTINGS_CATEGORIES,
                                                              PasswordSettings.SETTINGS_CATEGORY_WEAK);

        if (password.length() < requirements.getInt(PasswordSettings.SETTING_USER_MIN_LENGTH)) {
            return false;
        }

        if (requirements.get(PasswordSettings.SETTING_USER_REQUIRE_LETTERS_AND_DIGITS).asBoolean()
            && !containsLettersAndDigits(password)) {
            return false;
        }

        if (requirements.get(PasswordSettings.SETTING_USER_REQUIRE_UPPER_AND_LOWER_CASE).asBoolean()
            && !containsUpperAndLowerCase(password)) {
            return false;
        }

        return !requirements.get(PasswordSettings.SETTING_USER_REQUIRE_SPECIAL_CHARACTERS).asBoolean()
               || containsSpecialCharacters(password);
    }

    private boolean containsLettersAndDigits(String password) {
        return password.chars().anyMatch(Character::isLetter) && password.chars().anyMatch(Character::isDigit);
    }

    private boolean containsUpperAndLowerCase(String password) {
        return password.chars().anyMatch(Character::isUpperCase) && password.chars().anyMatch(Character::isLowerCase);
    }

    private boolean containsSpecialCharacters(String password) {
        return password.chars().anyMatch(c -> !Character.isLetterOrDigit(c));
    }
}
