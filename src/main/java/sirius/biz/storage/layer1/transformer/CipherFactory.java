/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer1.transformer;

import sirius.kernel.di.std.Named;
import sirius.kernel.settings.Extension;

/**
 * Represents a named factory which can use a given Layer 1 configuration and create a {@link CipherProvider}.
 * <p>
 * We need this kind of double indirection as a {@link CipherTransformer} which will use the created ciphers
 * can only be used once per operation. Still we want to keep the secret key etc. around, which is wrapped
 * in a {@link CipherProvider}. To permit several ways of encrpting data, this factory pattern is used
 * to create and initialize the appropriate provider.
 */
public interface CipherFactory extends Named {

    /**
     * Creates and initializes a {@link CipherProvider} based on the given config.
     *
     * @param spaceConfiguration the configuration to read settings from
     * @return a newly created and initialized provider
     * @throws Exception in case of an invalid configuration
     */
    CipherProvider create(Extension spaceConfiguration) throws Exception;
}
