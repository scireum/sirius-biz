/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.s3

import sirius.kernel.BaseSpecification
import sirius.kernel.commons.Files
import sirius.kernel.commons.Streams
import sirius.kernel.commons.Tuple
import sirius.kernel.di.std.Part

import java.nio.charset.StandardCharsets

class ObjectStoresSpec extends BaseSpecification {

    @Part
    private static ObjectStores stores

    def "createBucketWorks"() {
        File download
        when:
        File file = File.createTempFile("test", "")
        FileOutputStream fout = new FileOutputStream(file)
        for (int i = 0; i < 10024; i++) {
            fout.write("This is a test.".getBytes(StandardCharsets.UTF_8))
        }
        fout.close()
        and:
        stores.store().upload(stores.store().getBucketName("test"), "test", file, null)
        and:
        download = stores.store().download(stores.store().getBucketName("test"), "test")
        and:
        def expectedContents = com.google.common.io.Files.toString(file, StandardCharsets.UTF_8)
        def downloadedContents = com.google.common.io.Files.toString(download, StandardCharsets.UTF_8)
        then:
        expectedContents == downloadedContents
        cleanup:
        Files.delete(file)
        Files.delete(download)
    }

    def "PUT and GET works"() {
        File download
        when:
        File file = File.createTempFile("test", "")
        FileOutputStream fout = new FileOutputStream(file)
        for (int i = 0; i < 10024; i++) {
            fout.write("This is a test.".getBytes(StandardCharsets.UTF_8))
        }
        fout.close()
        and:
        stores.store().upload(stores.store().getBucketName("test"), "test", file, null)
        and:
        download = stores.store().download(stores.store().getBucketName("test"), "test")
        and:
        URLConnection c = new URL(stores.store().
                objectUrl(stores.store().getBucketName("test"), "test")).openConnection()
        and:
        def expectedContents = com.google.common.io.Files.toString(file, StandardCharsets.UTF_8)
        def downloadedContents = com.google.common.io.Files.toString(download, StandardCharsets.UTF_8)
        def downloadedData = new String(Streams.toByteArray(c.getInputStream()), StandardCharsets.UTF_8)
        then:
        expectedContents == downloadedData
        and:
        expectedContents == downloadedContents
        cleanup:
        Files.delete(file)
        Files.delete((File) download)
    }

    def "ensureBucketExists"() {
        when:
        stores.store().ensureBucketExists(stores.store().getBucketName("exists"))
        then:
        stores.store().doesBucketExist(stores.store().getBucketName("exists"))
        stores.bucketCache.get(Tuple.create(stores.store().name,
                stores.store().getBucketName("exists").getName()))
        !stores.store().doesBucketExist(stores.store().getBucketName("not-exists"))
        stores.bucketCache.get(Tuple.create(stores.store().name,
                stores.store().getBucketName("not-exists").getName())) == null
    }

    def "deleteBucket"() {
        when:
        stores.store().ensureBucketExists(stores.store().getBucketName("deleted"))
        then:
        stores.store().doesBucketExist(stores.store().getBucketName("deleted"))
        when:
        stores.store().deleteBucket(stores.store().getBucketName("deleted"))
        then:
        !stores.store().doesBucketExist(stores.store().getBucketName("deleted"))
        stores.bucketCache.get(Tuple.create(stores.store().name,
                stores.store().getBucketName("deleted").getName())) == null
    }

}
