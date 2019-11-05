/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer1.transformer;

import javax.crypto.Cipher;

/**
 * Keeps an initialized configuration around which permits to quickly create an encryption or decryption cipher.
 *
 * @see CipherFactory
 * @see CipherProvider
 */
public interface CipherProvider {

    /**
     * Creates an encryption cipher.
     *
     * @return the cipher to be used when encrypting data
     */
    Cipher createEncryptionCipher();

    /**
     * Creates an decryption cipher.
     *
     * @return the cipher to be used when decrypting data
     */
    Cipher createDecryptionChiper();
}
