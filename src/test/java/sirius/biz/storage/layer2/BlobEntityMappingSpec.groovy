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

import java.nio.file.Paths

class BlobEntityMappingSpec extends BaseSpecification {

    @Part
    private static OMA oma

    @Part
    private static BlobStorage blobStorage

    def "store entity with blob hard ref must make the blob non-temporary"() {
        given: 'an entity and a blob'
        def testEntity = new BlobRefEntity()
        Blob blob = blobStorage.getSpace("blob-files").createTemporaryBlob()
        expect: "the blob is temporary"
        blob.isTemporary()
        when: 'storing the blob as hard ref of the entity'
        testEntity.getBlobHardRef().setBlob(blob)
        oma.update(testEntity)
        testEntity = oma.refreshOrFail(testEntity)
        then: "the blob is no longer temporary"
        !testEntity.getBlobHardRef().getBlob().isTemporary()
    }

    def "store entity with unchanged blob hard ref must not fail"() {
        given: 'an entity with a hard ref blob'
        def testEntity = new BlobRefEntity()
        Blob blob = blobStorage.getSpace("blob-files").createTemporaryBlob()
        testEntity.getBlobHardRef().setBlob(blob)
        oma.update(testEntity)
        when: 'storing the entity without further edits'
        def loadedEntity = oma.findOrFail(BlobRefEntity.class, testEntity.getId())
        oma.update(loadedEntity)
        then: 'no exceptions must be thrown'
        noExceptionThrown()
    }

    def "store entity with unchanged blob soft ref must not fail"() {
        given: 'an entity with a soft ref blob'
        TenantsHelper.installTestTenant()
        def testEntity = new BlobRefEntity()
        def testFilePath = Paths.get("src/test/resources/test-data/test4blob.txt")
        Blob blob = blobStorage.getSpace("blob-files").findOrCreateByPath(testFilePath.toString())
        testEntity.getBlobSoftRef().setBlob(blob)
        oma.update(testEntity)
        when: 'storing the entity without further edits'
        def loadedEntity = oma.findOrFail(BlobRefEntity.class, testEntity.getId())
        oma.update(loadedEntity)
        then: 'no exceptions must be thrown'
        noExceptionThrown()
    }

    def "delete entity with blob hard ref must mark the blob for deletion"() {
        given: 'an entity with a blob hard ref'
        def testEntity = new BlobRefEntity()
        Blob blob = blobStorage.getSpace("blob-files").createTemporaryBlob()
        testEntity.getBlobHardRef().setBlob(blob)
        oma.update(testEntity)
        expect: 'new blob should be there and should not be marked for deletion'
        !blobStorage.getSpace("blob-files").findByBlobKey(blob.getBlobKey()).get().isDeleted()
        when: 'deleting entity'
        oma.delete(testEntity)
        then: 'blob with deleted referring entity should be gone'
        !blobStorage.getSpace("blob-files").findByBlobKey(blob.getBlobKey()).isPresent()
    }
}
