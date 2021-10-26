/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2.variants;

import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Named;
import sirius.kernel.settings.Extension;

import java.util.function.Function;

/**
 * Creates a new converter for a given variant and converter config.
 */
public interface ConverterFactory extends Named {

    /**
     * Creates a new instance using the given config supplier.
     *
     * @param configSupplier  a function which supplies the config values for the variant and converter for a key
     * @return a new converter instance which can be used to generated variants according to the given configurations
     */
    Converter createConverter(Function<String, Value> configSupplier);
}
