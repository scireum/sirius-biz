/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.s3

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import sirius.kernel.SiriusExtension
import sirius.kernel.commons.Files
import sirius.kernel.commons.Streams
import sirius.kernel.commons.Tuple
import sirius.kernel.di.std.Part
import java.io.File
import java.io.FileOutputStream
import java.net.URL

import java.nio.charset.StandardCharsets
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExtendWith(SiriusExtension::class)
class ObjectStoresTest {

    @Test
    fun `createBucketWorks`() {
        val file = File.createTempFile("test", "")
        val fout = FileOutputStream(file)
        repeat(10024) {
            fout.write("This is a test.".toByteArray(StandardCharsets.UTF_8))
        }
        fout.close()
        stores.store().upload(stores.store().getBucketName("test"), "test", file, null)
        val download = stores.store().download(stores.store().getBucketName("test"), "test")
        val expectedContents = com.google.common.io.Files.toString(file, StandardCharsets.UTF_8)
        val downloadedContents = com.google.common.io.Files.toString(download, StandardCharsets.UTF_8)
        assertEquals(expectedContents, downloadedContents)
        Files.delete(file)
        Files.delete(download)
    }

    @Test
    fun `PUT and GET works`() {
        val file = File.createTempFile("test", "")
        val fout = FileOutputStream(file)
        repeat(10024) {
            fout.write("This is a test.".toByteArray(StandardCharsets.UTF_8))
        }
        fout.close()
        stores.store().upload(stores.store().getBucketName("test"), "test", file, null)
        val download = stores.store().download(stores.store().getBucketName("test"), "test")
        val c = URL(
            stores.store().objectUrl(stores.store().getBucketName("test"), "test")
        ).openConnection()
        val expectedContents = com.google.common.io.Files.toString(file, StandardCharsets.UTF_8)
        val downloadedContents = com.google.common.io.Files.toString(download, StandardCharsets.UTF_8)
        val downloadedData = String(Streams.toByteArray(c.getInputStream()), StandardCharsets.UTF_8)
        assertEquals(expectedContents, downloadedData)
        assertEquals(expectedContents, downloadedContents)
        Files.delete(file)
        Files.delete(download)
    }

    @Test
    fun `ensureBucketExists`() {
        stores.store().ensureBucketExists(stores.store().getBucketName("exists"))
        stores.store().doesBucketExist(stores.store().getBucketName("exists"))
        stores.bucketCache.get(
            Tuple.create(
                stores.store().name,
                stores.store().getBucketName("exists").getName()
            )
        )
        !stores.store().doesBucketExist(stores.store().getBucketName("not-exists"))
        assertEquals(
            null, stores.bucketCache.get(
                Tuple.create(
                    stores.store().name,
                    stores.store().getBucketName("not-exists").getName()
                )
            )
        )
    }

    @Test
    fun `deleteBucket`() {
        stores.store().ensureBucketExists(stores.store().getBucketName("deleted"))
        stores.store().doesBucketExist(stores.store().getBucketName("deleted"))
        stores.store().deleteBucket(stores.store().getBucketName("deleted"))
        assertTrue { !stores.store().doesBucketExist(stores.store().getBucketName("deleted")) }
        assertEquals(
            null, stores.bucketCache.get(
                Tuple.create(
                    stores.store().name,
                    stores.store().getBucketName("deleted").getName()
                )
            )
        )
    }

    companion object {
        @Part
        @JvmStatic
        private lateinit var stores: ObjectStores

    }
}
