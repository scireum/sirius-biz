/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.s3

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import sirius.kernel.SiriusExtension
import sirius.kernel.commons.Files
import sirius.kernel.commons.Tuple
import sirius.kernel.di.std.Part
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.util.Random
import java.util.concurrent.TimeUnit
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.nio.file.Files as files_

/**
 * Tests the [ObjectStores].
 */
@ExtendWith(SiriusExtension::class)
class ObjectStoresTest {

    @Test
    fun `Create bucket works`() {
        val file = File.createTempFile("test", "")
        val outputStream = FileOutputStream(file)
        repeat(10024) {
            outputStream.write("This is a test.".toByteArray(StandardCharsets.UTF_8))
        }
        outputStream.close()
        stores.store().upload(stores.store().getBucketName("test"), "test", file, null)
        val download = stores.store().download(stores.store().getBucketName("test"), "test")
        val expectedContents = files_.readString(file.toPath(), StandardCharsets.UTF_8)
        val downloadedContents = files_.readString(download.toPath(), StandardCharsets.UTF_8)
        assertEquals(expectedContents, downloadedContents)
        Files.delete(file)
        Files.delete(download)
    }

    @Test
    fun `PUT and GET works`() {
        val file = File.createTempFile("test", "")
        val outputStream = FileOutputStream(file)
        repeat(10024) {
            outputStream.write("This is a test.".toByteArray(StandardCharsets.UTF_8))
        }
        outputStream.close()
        stores.store().upload(stores.store().getBucketName("test"), "test", file, null)
        val download = stores.store().download(stores.store().getBucketName("test"), "test")
        val c = URI(
            stores.store().objectUrl(stores.store().getBucketName("test"), "test")
        ).toURL().openConnection()

        val expectedContents = files_.readString(file.toPath(), StandardCharsets.UTF_8)
        val downloadedContents = files_.readString(download.toPath(), StandardCharsets.UTF_8)
        val downloadedData = String(c.getInputStream().readAllBytes(), StandardCharsets.UTF_8)

        assertEquals(expectedContents, downloadedData)
        assertEquals(expectedContents, downloadedContents)
        Files.delete(file)
        Files.delete(download)
    }

    @Test
    fun `Ensure bucket exists`() {
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
    fun `Delete bucket works`() {
        stores.store().ensureBucketExists(stores.store().getBucketName("deleted"))
        stores.store().doesBucketExist(stores.store().getBucketName("deleted"))
        stores.store().deleteBucket(stores.store().getBucketName("deleted"))
        assertFalse { stores.store().doesBucketExist(stores.store().getBucketName("deleted")) }
        assertEquals(
            null, stores.bucketCache.get(
                Tuple.create(
                    stores.store().name,
                    stores.store().getBucketName("deleted").name
                )
            )
        )
    }

    @Test
    fun `Multipart upload of a stream with an unknown length works`() {
        val bucket = stores.store().getBucketName("multipart")
        // Sizing the payload from the production threshold rather than from a literal keeps it spanning at least three
        // chunks even if that threshold is ever changed, so the chunking loop cannot quietly fall out of coverage.
        // Asserting the part count directly is not an option here: s3-ninja answers with a plain hash instead of the
        // "<hash>-<parts>" ETag that S3 itself returns for a multipart object.
        val payload = generateRandomData(3 * multipartChunkThreshold())

        // Passing a length of zero is the documented way of announcing an unknown length and is what routes into the
        // multipart path, so uploading this way covers the dispatch as well as the chunking itself.
        stores.store().upload(bucket, "large", ByteArrayInputStream(payload), 0L)

        val download = stores.store().download(bucket, "large")
        try {
            // Compares digests instead of the arrays themselves: a failed content assertion over 21 MiB renders a
            // message so large that the surefire reporter fails while writing it and drops the test from its report.
            assertEquals(payload.size.toLong(), download.length())
            assertEquals(sha256(payload), sha256(files_.readAllBytes(download.toPath())))
        } finally {
            Files.delete(download)
        }
    }

    @Test
    fun `Multipart upload of an empty stream creates an empty object`() {
        val bucket = stores.store().getBucketName("multipart")

        stores.store().upload(bucket, "empty", ByteArrayInputStream(ByteArray(0)))

        val download = stores.store().download(bucket, "empty")
        try {
            assertEquals(0, download.length())
        } finally {
            Files.delete(download)
        }
    }

    @Test
    fun `Listing objects honours the given prefix`() {
        val bucket = stores.store().getBucketName("listing")
        uploadSmallObject(bucket, "prefixed/a")
        uploadSmallObject(bucket, "prefixed/b")
        uploadSmallObject(bucket, "other/c")

        val keys = mutableListOf<String>()
        stores.store().listObjects(bucket, "prefixed/") {
            keys.add(it.key())
            true
        }

        assertEquals(listOf("prefixed/a", "prefixed/b"), keys.sorted())
    }

    @Test
    fun `Listing objects stops once the consumer returns false`() {
        val bucket = stores.store().getBucketName("listing-abort")
        uploadSmallObject(bucket, "a")
        uploadSmallObject(bucket, "b")

        var seen = 0
        stores.store().listObjects(bucket, null) {
            seen++
            false
        }

        assertEquals(1, seen)
    }

    // Broken continuation handling makes the loop in listObjects re-fetch the first page forever rather than fail, so
    // the timeout is what turns that regression into a red build instead of a stalled pipeline.
    @Test
    @Timeout(value = 2, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    fun `Listing objects pages through truncated responses`() {
        val bucket = stores.store().getBucketName("paging")
        // A single response is capped at 1000 keys, so 1001 objects force a second request via the continuation token.
        repeat(1001) { uploadSmallObject(bucket, "page-$it") }

        // Guards the premise of this test: if the store ever stopped truncating, the continuation handling below
        // would silently go unexercised while the assertion on the object count still passed.
        val firstPage = stores.store()
            .getClient()
            .listObjectsV2(
                ListObjectsV2Request.builder().bucket(bucket.name).prefix("page-").build()
            )
        assertTrue(firstPage.isTruncated == true, "Expected a truncated response, but the store returned all keys")

        // Collects the keys rather than counting calls, so that a page served twice cannot be mistaken for progress.
        val keys = mutableSetOf<String>()
        stores.store().listObjects(bucket, "page-") {
            keys.add(it.key())
            true
        }

        assertEquals(1001, keys.size)
    }

    @Test
    fun `Copying an object between buckets works`() {
        val sourceBucket = stores.store().getBucketName("copy-source")
        val targetBucket = stores.store().getBucketName("copy-target")
        uploadSmallObject(sourceBucket, "original")
        stores.store().ensureBucketExists(targetBucket)

        stores.store().copyObject(sourceBucket, "original", targetBucket, "copy")

        val download = stores.store().download(targetBucket, "copy")
        try {
            assertEquals("original", files_.readString(download.toPath(), StandardCharsets.UTF_8))
        } finally {
            Files.delete(download)
        }
    }

    @Test
    fun `Downloading a missing object is reported as a missing file`() {
        val bucket = stores.store().getBucketName("missing")
        stores.store().ensureBucketExists(bucket)

        assertFailsWith<FileNotFoundException> { stores.store().download(bucket, "no-such-object") }
    }

    @Test
    fun `Async download fulfills the promise with the object data`() {
        val bucket = stores.store().getBucketName("async")
        uploadSmallObject(bucket, "async-test")

        val promise = stores.store().downloadAsync(bucket, "async-test")

        assertTrue(promise.await(Duration.ofSeconds(30)), "The download did not complete within the timeout")
        // await() only reports completion, which a failed download satisfies as well - hence the explicit check,
        // without which a failing download would surface as a bare NPE on the handle below.
        assertTrue(promise.isSuccessful, "The download failed: ${promise.failure}")
        promise.get().use { handle ->
            assertContentEquals("async-test".toByteArray(StandardCharsets.UTF_8), handle.file.readBytes())
        }
    }

    companion object {
        private const val RANDOM_SEED = 42L

        @Part
        @JvmStatic
        private lateinit var stores: ObjectStores

        /**
         * Uploads a small object whose contents are its own key, so that assertions can identify it.
         */
        private fun uploadSmallObject(bucket: BucketName, key: String) {
            val data = key.toByteArray(StandardCharsets.UTF_8)
            stores.store().upload(bucket, key, ByteArrayInputStream(data), data.size.toLong())
        }

        /**
         * Determines the size at which [ObjectStore] flushes an aggregated chunk, so that a payload can be sized to
         * span several of them without restating the constant.
         */
        private fun multipartChunkThreshold(): Int {
            val field = ObjectStore::class.java.getDeclaredField("MAXIMAL_LOCAL_AGGREGATION_BUFFER_SIZE")
            field.isAccessible = true
            return field.getInt(null)
        }

        /**
         * Generates deterministic pseudo random data, so that a failed content comparison can be reproduced by
         * simply running the test again.
         */
        private fun generateRandomData(length: Int): ByteArray {
            val result = ByteArray(length)
            Random(RANDOM_SEED).nextBytes(result)
            return result
        }

        /**
         * Digests the given payload so that large contents can be compared without rendering them into an assertion
         * message.
         */
        private fun sha256(data: ByteArray): String {
            return MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
        }
    }
}
