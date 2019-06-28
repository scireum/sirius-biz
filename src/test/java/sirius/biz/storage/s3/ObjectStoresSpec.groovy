/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.s3

import com.google.common.base.Charsets
import com.google.common.io.ByteStreams
import com.google.common.io.Files
import sirius.kernel.BaseSpecification
import sirius.kernel.commons.Tuple
import sirius.kernel.di.std.Part

class ObjectStoresSpec extends BaseSpecification {

    @Part
    private static ObjectStores stores

    def "createBucketWorks"() {
        File download
        when:
        File file = File.createTempFile("test", "")
        for (int i = 0; i < 10024; i++) {
            Files.append("This is a test.", file, Charsets.UTF_8)
        }
        and:
        stores.store().upload(stores.store().getBucketName("test"), "test", file, null)
        and:
        download = stores.store().download(stores.store().getBucketName("test"), "test")
        then:
        Files.toString(file, Charsets.UTF_8) == Files.toString(download, Charsets.UTF_8)
        cleanup:
        sirius.kernel.commons.Files.delete(file)
        sirius.kernel.commons.Files.delete((File) download)
    }

    def "PUT and GET works"() {
        File download
        when:
        File file = File.createTempFile("test", "")
        for (int i = 0; i < 10024; i++) {
            Files.append("This is a test.", file, Charsets.UTF_8)
        }
        and:
        stores.store().upload(stores.store().getBucketName("test"), "test", file, null)
        and:
        download = stores.store().download(stores.store().getBucketName("test"), "test")
        and:
        URLConnection c = new URL(stores.store().
                                          objectUrl(stores.store().getBucketName("test"), "test")).openConnection()
        and:
        String downloadedData = new String(ByteStreams.toByteArray(c.getInputStream()), Charsets.UTF_8)
        then:
        Files.toString(file, Charsets.UTF_8) == Files.toString(download, Charsets.UTF_8)
        and:
        downloadedData == Files.toString(file, Charsets.UTF_8)
        cleanup:
        sirius.kernel.commons.Files.delete(file)
        sirius.kernel.commons.Files.delete((File) download)
    }

    def "ensureBucketExists"() {
        when:
        stores.store().ensureBucketExists(stores.store().getBucketName("exists"))
        then:
        stores.store().doesBucketExist(stores.store().getBucketName("exists")) == true
        stores.bucketCache.get(Tuple.create(stores.store().name,
                                            stores.store().getBucketName("exists").getName())) == true
        stores.store().doesBucketExist(stores.store().getBucketName("not-exists")) == false
        stores.bucketCache.get(Tuple.create(stores.store().name,
                                            stores.store().getBucketName("not-exists").getName())) == null
    }

    def "deleteBucket"() {
        when:
        stores.store().ensureBucketExists(stores.store().getBucketName("deleted"))
        then:
        stores.store().doesBucketExist(stores.store().getBucketName("deleted")) == true
        when:
        stores.store().deleteBucket(stores.store().getBucketName("deleted"))
        then:
        stores.store().doesBucketExist(stores.store().getBucketName("deleted")) == false
        stores.bucketCache.get(Tuple.create(stores.store().name,
                                            stores.store().getBucketName("deleted").getName())) == null
    }
}
