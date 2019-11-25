/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2.variants;

import sirius.kernel.di.std.Named;
import sirius.kernel.settings.Extension;

/**
 * Creates a new converter for a given variant and converter config.
 */
public interface ConverterFactory extends Named {

    /**
     * Creates a new instance using the given configs.
     *
     * @param variantConfig   the configuration which specifies the actual variant config to apply
     * @param converterConfig the base configuration which provides standard settings to the converter
     * @return a new converter instance which can be used to generated variants according to the given configurations
     */
    Converter createConverter(Extension variantConfig, Extension converterConfig);
}
