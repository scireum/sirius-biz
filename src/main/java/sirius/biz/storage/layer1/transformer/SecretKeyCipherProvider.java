/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer1.transformer;

import sirius.biz.storage.util.StorageUtils;
import sirius.kernel.health.Exceptions;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;

/**
 * Represents a provider which uses a {@link SecretKey} to create an appropriate encryption and decrpytion {@link Cipher}.
 */
public class SecretKeyCipherProvider implements CipherProvider {

    private final SecretKey secretKey;
    private final String algorithm;

    /**
     * Creates a new instance of an algorithm for the given key.
     *
     * @param secretKey the key to use
     * @param algorithm the algorithm to use
     */
    public SecretKeyCipherProvider(SecretKey secretKey, String algorithm) {
        this.secretKey = secretKey;
        this.algorithm = algorithm;
    }

    @Override
    public Cipher createEncryptionCipher() {
        try {
            Cipher cipher = Cipher.getInstance(algorithm);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            return cipher;
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage("Layer 1: Failed to create an encryption cipher: %s (%s)")
                            .handle();
        }
    }

    @Override
    public Cipher createDecryptionChiper() {
        try {
            Cipher cipher = Cipher.getInstance(algorithm);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            return cipher;
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage("Layer 1: Failed to create an encryption cipher: %s (%s)")
                            .handle();
        }
    }
}
