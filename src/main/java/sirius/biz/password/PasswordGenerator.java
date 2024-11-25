/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.password;

import sirius.db.mixing.Entity;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.settings.Settings;

import java.security.SecureRandom;
import java.util.stream.IntStream;

/**
 * Generates random passwords.
 */
@Register(classes = PasswordGenerator.class)
public class PasswordGenerator {

    @Part
    private PasswordSettings passwordSettings;

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Generates a random password based on the settings that apply to the given entity.
     *
     * @param entity the entity to generate a password for
     * @return the generated password
     */
    public String generatePassword(Entity entity) {
        Settings settings = passwordSettings.resolveGeneratedPasswordSettings(entity);
        int effectiveLength = settings.get(PasswordSettings.SETTING_GENERATED_LENGTH).asInt(8);
        String effectiveCharacters = settings.getString(PasswordSettings.SETTING_GENERATED_CONSIDERED_CHARACTERS);

        return IntStream.range(0, effectiveLength)
                        .mapToObj(i -> effectiveCharacters.charAt(RANDOM.nextInt(effectiveCharacters.length())))
                        .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append)
                        .toString();
    }
}
