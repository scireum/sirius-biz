/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2.mongo;

import sirius.biz.storage.layer2.BlobStorage;
import sirius.biz.storage.layer2.BlobStorageSpace;
import sirius.kernel.di.std.Register;
import sirius.kernel.settings.Extension;

/**
 * Provides the adapter to make the MongoDB based implementation the metadata storage for the layer 2.
 */
@Register(classes = BlobStorage.class, framework = MongoBlobStorage.FRAMEWORK_MONGO_BLOB_STORAGE)
public class MongoBlobStorage extends BlobStorage {

    /**
     * Names the framework which must be enabled to store the blob metadata in the associated MongoDB.
     */
    public static final String FRAMEWORK_MONGO_BLOB_STORAGE = "biz.storage-blob-mongo";

    @Override
    protected BlobStorageSpace createSpace(Extension config) {
        return new MongoBlobStorageSpace(config.getId(), config);
    }
}
