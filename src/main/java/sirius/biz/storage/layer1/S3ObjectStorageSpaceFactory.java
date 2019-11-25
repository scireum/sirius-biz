/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer1;

import sirius.biz.storage.util.StorageUtils;
import sirius.kernel.di.std.Register;
import sirius.kernel.settings.Extension;

import javax.annotation.Nonnull;

/**
 * Creates an instance of {@link S3ObjectStorageSpace} for each Layer 1 storage space which specified <b>s3</b> as engine.
 */
@Register(framework = StorageUtils.FRAMEWORK_STORAGE)
public class S3ObjectStorageSpaceFactory implements ObjectStoraceSpaceFactory {

    @Override
    public ObjectStorageSpace create(String name, Extension extension) throws Exception {
        return new S3ObjectStorageSpace(name, extension);
    }

    @Nonnull
    @Override
    public String getName() {
        return "s3";
    }
}
