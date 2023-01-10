/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2;

import sirius.biz.storage.layer2.variants.BlobVariant;
import sirius.biz.storage.layer2.variants.ConversionEngine;
import sirius.biz.storage.layer2.variants.ConversionProcess;
import sirius.kernel.di.std.PriorityParts;
import sirius.kernel.di.std.Register;

import java.util.List;

/**
 * Is notified if the {@link ConversionEngine#performConversion(ConversionProcess) conversion} of a {@link BlobVariant variant}
 * finally {@link BlobVariant#isFailed() failed} and propagates the error to registered {@link FailedVariantConversionHandler handlers}
 * for further processing.
 */
@Register(classes = FailedVariantConversionListener.class)
public class FailedVariantConversionListener {
    @PriorityParts(FailedVariantConversionHandler.class)
    private List<FailedVariantConversionHandler> errorHandlers;

    /**
     * Propagates the conversion error to registered {@link FailedVariantConversionHandler handlers} for further processing.
     *
     * @param error     the error causing the conversion failure
     * @param blobKey   the blob for which the variant should have been created
     * @param variantId the failed variant
     */
    public void propagateError(Throwable error, String blobKey, String variantId) {
        errorHandlers.forEach(handler -> handler.handle(error, blobKey, variantId));
    }
}
