/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.model;

import com.google.common.io.BaseEncoding;
import org.apache.commons.io.Charsets;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Priorized;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.spec.KeySpec;

/**
 * Uses the PBKDF2 function (with SHA-256 and 65536 iterations) to "hash" passwords.
 * <p>
 * This is a purpose build derivation function for passwords which is supplied by the JDK
 * and considered strong enough for user passwords.
 * <p>
 * If the provided function isn't strong enough for the application at hands, it can be subclassed
 * and {@link #getPriority()} and {@link #isOutdated()} can be overwritten to disable this.
 * <p>
 * At the same time, another (non-outdated) function has to be provided which also has a lower
 * (better) priority as this one.
 */
@Register
public class PBKDF2SHA256Function implements PasswordHashFunction {

    private static final int NUMBER_OF_RUNS = 65536;
    private static final int KEY_LENGTH = 256;

    @Nullable
    @Override
    public String computeHash(@Nullable String salt, @Nonnull String password) {
        try {
            if (Strings.isEmpty(salt)) {
                return null;
            }

            KeySpec spec =
                    new PBEKeySpec(password.toCharArray(), salt.getBytes(Charsets.UTF_8), NUMBER_OF_RUNS, KEY_LENGTH);
            SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = f.generateSecret(spec).getEncoded();
            return BaseEncoding.base64().encode(hash);
        } catch (Exception e) {
            Exceptions.handle(Log.APPLICATION, e);
            return null;
        }
    }

    @Override
    public boolean isOutdated() {
        return false;
    }

    @Override
    public int getPriority() {
        return Priorized.DEFAULT_PRIORITY;
    }
}
