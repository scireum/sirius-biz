/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2.variants;

import sirius.biz.storage.layer1.FileHandle;
import sirius.biz.storage.layer2.Blob;

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
     *
     * @param blob the blob which provides the raw data (or another variant as base data).
     * @return the generated file wrapped as {@link FileHandle}. Note that this file must exist and be non empty.
     * @throws Exception in case of a conversion error
     */
    FileHandle performConversion(Blob blob) throws Exception;
}
