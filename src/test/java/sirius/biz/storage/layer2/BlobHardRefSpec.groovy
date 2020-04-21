/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2

import sirius.db.jdbc.OMA
import sirius.kernel.BaseSpecification
import sirius.kernel.di.std.Part

class BlobHardRefSpec extends BaseSpecification {

    @Part
    private static OMA oma;

    @Part
    private static BlobStorage blobStorage;

    def "store entity with unchanged hard ref blob must not fail"() {
        given:
        def testEntity = new BlobHardRefEntity()
        Blob blob = blobStorage.getSpace("blob-files").createTemporaryBlob()
        testEntity.getTheBlobRef().setBlob(blob)
        oma.update(testEntity)
        when:
        def loadedEntity = oma.
                select(BlobHardRefEntity.class).
                eq(BlobHardRefEntity.ID, testEntity.getId()).
                first().
                get()
        oma.update(loadedEntity)
        then: 'no exceptions must be thrown'
        noExceptionThrown()
    }

}
