/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2;

import sirius.biz.web.Autoloaded;
import sirius.db.jdbc.SQLEntity;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.types.BaseEntityRef;

public class BlobRefEntity extends SQLEntity {

    public static final Mapping BLOB_HARD_REF = Mapping.named("blobHardRef");
    @NullAllowed
    @Autoloaded
    private final BlobHardRef blobHardRef = new BlobHardRef("blob-files");

    public static final Mapping BLOB_SOFT_REF = Mapping.named("blobSoftRef");
    @NullAllowed
    private final BlobSoftRef blobSoftRef = new BlobSoftRef("blob-files", BaseEntityRef.OnDelete.IGNORE);

    public BlobHardRef getBlobHardRef() {
        return blobHardRef;
    }

    public BlobSoftRef getBlobSoftRef() {
        return blobSoftRef;
    }
}
