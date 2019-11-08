/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer1;

import sirius.kernel.di.std.Named;
import sirius.kernel.settings.Extension;

/**
 * Represents a factory which instantiates the proper {@link ObjectStorageSpace} for a given configuration.
 * <p>
 * The effective factory is selected by the {@link ObjectStorage#CONFIG_KEY_LAYER1_ENGINE} setting.
 */
public interface ObjectStoraceSpaceFactory extends Named {

    /**
     * Creates a {@link ObjectStorageSpace} for the given Layer 1 storage space.
     *
     * @param name      the name of the space to create
     * @param extension the configuration as given in the system config
     * @return the newly created instance
     * @throws Exception in case of a configuration error
     */
    ObjectStorageSpace create(String name, Extension extension) throws Exception;
}
