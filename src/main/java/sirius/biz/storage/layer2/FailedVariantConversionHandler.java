/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2;

import sirius.biz.storage.layer2.variants.BlobVariant;
import sirius.kernel.di.std.AutoRegister;
import sirius.kernel.di.std.Priorized;

/**
 * Can be implemented to handle {@link BlobVariant variants} if their conversion finally {@link BlobVariant#isFailed() failed}.
 * <p>
 * Implementing handlers need to be {@link sirius.kernel.di.std.Register registered}.
 */
@AutoRegister
public interface FailedVariantConversionHandler extends Priorized {

    /**
     * Handles the finally failed conversion of the given variant.
     * <p>
     * This can be used to perform additional actions, like notifying upstream entities about the failed variant and
     * the corresponding blob.
     *
     * @param error     the error causing the conversion failure
     * @param blobKey   the blob for which the variant should have been created
     * @param variantId the failed variant
     */
    void handle(Throwable error, String blobKey, String variantId);
}
