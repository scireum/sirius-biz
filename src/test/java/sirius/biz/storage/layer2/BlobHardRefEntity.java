/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2;

import sirius.db.jdbc.SQLEntity;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.NullAllowed;

public class BlobHardRefEntity extends SQLEntity {

    public static final Mapping THE_BLOB_REF = Mapping.named("theBlobRef");
    @NullAllowed
    private final BlobHardRef theBlobRef = new BlobHardRef("blob-files");

    public BlobHardRef getTheBlobRef() {
        return theBlobRef;
    }
}
