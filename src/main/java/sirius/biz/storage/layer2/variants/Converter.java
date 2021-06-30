/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2.variants;

import sirius.biz.storage.layer1.FileHandle;
import sirius.kernel.commons.Producer;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Provides the converter logic to generate a certain kind of {@link sirius.biz.storage.layer2.variants.BlobVariant}.
 * <p>
 * Instances are managed by the {@link ConversionEngine} which creates them via a {@link ConverterFactory}. Note
 * that only one instance per variant configuration is generated. Therefore implementing classes need to be
 * fully stateless and are therefore threadsafe.
 */
@ThreadSafe
public interface Converter {

    /**
     * Performs the generation of the requested variant.
     * <p>
     * Note that {@link ConversionProcess#withConversionResult(FileHandle)} has to be used and that
     * {@link ConversionProcess#recordConversionDuration(long)} and {@link ConversionProcess#download(Producer)}
     * should be used to provide proper conversion metrics.
     *
     * @param conversionProcess used to obtain the input parameter and also to return the resulting file and some
     *                          statistics.
     * @throws Exception in case of a conversion error
     */
    void performConversion(ConversionProcess conversionProcess) throws Exception;
}
