package sirius.biz.storage.layer2


import org.junit.jupiter.api.extension.ExtendWith
import sirius.kernel.SiriusExtension
import org.junit.jupiter.api.Test


import sirius.biz.tenants.TenantsHelper
import sirius.db.jdbc.OMA
import sirius.kernel.di.std.Part
import java.nio.file.Paths
import org.junit.jupiter.api.assertDoesNotThrow
import sirius.biz.storage.layer2.jdbc.SQLBlob
import kotlin.test.assertTrue

@ExtendWith(SiriusExtension::class)
class SQLBlobEntityMappingTest {
    @Test
    fun `store entity with blob hard ref must make the blob non-temporary`() {
        val blob = blobStorage.getSpace("blob-files").createTemporaryBlob()
        var testEntity = BlobRefEntity()
        assertTrue { blob.isTemporary() }
        testEntity.getBlobHardRef().setBlob(blob)
        oma.update(testEntity)
        testEntity = oma.refreshOrFail(testEntity)
        assertTrue { !testEntity.getBlobHardRef().getBlob()!!.isTemporary() }
    }

    @Test
    fun `store entity with unchanged blob hard ref must not fail`() {
        val testEntity = BlobRefEntity()
        val blob = blobStorage.getSpace("blob-files").createTemporaryBlob()
        testEntity.getBlobHardRef().setBlob(blob)
        oma.update(testEntity)
        val loadedEntity = oma.findOrFail(BlobRefEntity::class.java, testEntity.getId())
        assertDoesNotThrow { oma.update(loadedEntity) }
    }

    @Test
    fun `store entity with unchanged blob soft ref must not fail`() {
        TenantsHelper.installTestTenant()
        val testEntity = BlobRefEntity()
        val testFilePath = Paths.get("src/test/resources/test-data/test4blob.txt")
        val blob = blobStorage.getSpace("blob-files").findOrCreateByPath(testFilePath.toString())
        testEntity.getBlobSoftRef().setBlob(blob)
        oma.update(testEntity)
        val loadedEntity = oma.findOrFail(BlobRefEntity::class.java, testEntity.getId())
        assertDoesNotThrow { oma.update(loadedEntity) }
    }

    @Test
    fun `delete entity with blob hard ref must mark the blob for deletion`() {
        val testEntity = BlobRefEntity()
        val blob = blobStorage.getSpace("blob-files").createTemporaryBlob()
        testEntity.getBlobHardRef().setBlob(blob)
        oma.update(testEntity)
        val Blob: SQLBlob? = blobStorage.getSpace("blob-files").findByBlobKey(blob.getBlobKey()).get() as? SQLBlob
        if (Blob != null) {
            assertTrue { !Blob.isDeleted() }
        }
        assertTrue { Blob != null }
        oma.delete(testEntity)
        assertTrue { !blobStorage.getSpace("blob-files").findByBlobKey(blob.getBlobKey()).isPresent() }
    }

    companion object {
        @Part
        @JvmStatic
        private lateinit var oma: OMA

        @Part
        @JvmStatic
        private lateinit var blobStorage: BlobStorage
    }
}
