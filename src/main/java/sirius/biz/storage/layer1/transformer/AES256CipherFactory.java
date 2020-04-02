/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer1.transformer;

import java.nio.charset.StandardCharsets;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Register;
import sirius.kernel.settings.Extension;

import javax.annotation.Nonnull;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.spec.KeySpec;

/**
 * Represents a factory which provides an <b>AES 256</b> cipher.
 * <p>
 * This will use the <tt>passphrase</tt> from the space config and apply an <tt>PBKDF2WithHmacSHA256</tt> key
 * derivation function using 65536 iterations and a target key length of 256. It will then yield a secret key
 * wrapped in a {@link SecretKeyCipherProvider} to be used for <tt>AES/CBC/PKCS5Padding</tt>.
 */
@Register
public class AES256CipherFactory implements CipherFactory {

    private static final String CONFIG_KEY_PASSPHRASE = "passphrase";
    private static final String KEY_DERIVATION_FUNCTION = "PBKDF2WithHmacSHA256";
    private static final String SALT = "2d61d2f2176a80eae6816b839567315d";
    private static final int NUM_ITERATIONS = 65536;
    private static final int KEY_LENGTH = 256;
    private static final String KEY_ALGORITHM_NAME = "AES";
    private static final String CIPHER_NAME = "AES/ECB/PKCS5Padding";

    @Nonnull
    @Override
    public String getName() {
        return "aes256";
    }

    @Override
    public CipherProvider create(Extension spaceConfiguration) throws Exception {
        String passphrase = spaceConfiguration.require(CONFIG_KEY_PASSPHRASE).asString();

        if (Strings.isEmpty(passphrase)) {
            throw new IllegalArgumentException(
                    "'passphrase' must not be empty when performing AES encryption/decryption!");
        }

        SecretKeyFactory factory = SecretKeyFactory.getInstance(KEY_DERIVATION_FUNCTION);
        KeySpec spec =
                new PBEKeySpec(passphrase.toCharArray(), SALT.getBytes(StandardCharsets.UTF_8), NUM_ITERATIONS, KEY_LENGTH);
        SecretKey tmp = factory.generateSecret(spec);
        SecretKeySpec secretKey = new SecretKeySpec(tmp.getEncoded(), KEY_ALGORITHM_NAME);

        return new SecretKeyCipherProvider(secretKey, CIPHER_NAME);
    }
}
