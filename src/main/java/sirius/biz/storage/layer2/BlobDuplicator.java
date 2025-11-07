/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2;

import sirius.db.KeyGenerator;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;

import java.util.HashSet;
import java.util.Set;

/**
 * Defines parameters used to duplicate a {@link Blob} in a new temporary copy.
 */
public class BlobDuplicator {

    @Part
    private static BlobStorage blobStorage;

    @Part
    private static KeyGenerator keyGenerator;

    private final Blob blob;
    private String storageSpace;
    private final Set<String> variants = new HashSet<>();

    /**
     * Creates a new duplicator for the given blob.
     *
     * @param blob the blob to duplicate
     * @return a new duplicator for the given blob
     */
    public BlobDuplicator(Blob blob) {
        this.blob = blob;
        this.storageSpace = blob.getSpaceName();
    }

    /**
     * Adds a variant name to be duplicated among with the blob.
     *
     * @param variantName the variant name to duplicate
     * @return the duplicator itself for fluent method calls
     */
    public BlobDuplicator withVariant(String variantName) {
        this.variants.add(variantName);
        return this;
    }

    /**
     * Adds all variants of the blob to be duplicated.
     *
     * @return the duplicator itself for fluent method calls
     */
    public BlobDuplicator withAllVariants() {
        blob.fetchVariants().forEach(blobVariant -> variants.add(blobVariant.getVariantName()));
        return this;
    }

    /**
     * Defines storage space to use for the duplicated blob.
     * <p>
     * By default, the new blob is created in the same space as the original one. Note that if the space is not
     * in the same storage system as the original blob, the copy might not be performant.
     *
     * @param storageSpace the storage space to use for the duplicated blob
     * @return the duplicator itself for fluent method calls
     */
    public BlobDuplicator withStorageSpace(String storageSpace) {
        this.storageSpace = storageSpace;
        return this;
    }

    /**
     * Returns the temporary blob as a replica of the original one.
     *
     * @return the cloned blob
     */
    @SuppressWarnings("unchecked")
    public Blob execute() {
        Blob newBlob = blobStorage.getSpace(storageSpace).createTemporaryBlob(blob.getTenantId());
        BasicBlobStorageSpace space = (BasicBlobStorageSpace) newBlob.getStorageSpace();
        try {
            String newKey = keyGenerator.generateId();
            blob.getStorageSpace()
                .getPhysicalSpace()
                .duplicatePhysicalObject(blob.getPhysicalObjectKey(), newKey, storageSpace);
            space.updateBlob(newBlob, newKey, blob.getSize(), blob.getFilename(), blob.getCheckSum());
        } catch (Exception exception) {
            throw Exceptions.createHandled().error(exception).handle();
        }

        blob.fetchVariants()
            .stream()
            .filter(blobVariant -> variants.contains(blobVariant.getVariantName()))
            .forEach(blobVariant -> {
                String newKey = keyGenerator.generateId();
                blob.getStorageSpace()
                    .getPhysicalSpace()
                    .duplicatePhysicalObject(blobVariant.getPhysicalObjectKey(), newKey, storageSpace);
                space.createVariant(newBlob,
                                    blobVariant.getVariantName(),
                                    newKey,
                                    blobVariant.getSize(),
                                    blobVariant.getCheckSum());
            });

        return newBlob;
    }
}
