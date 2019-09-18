/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer1

import com.google.common.base.Charsets
import com.google.common.io.CharStreams
import sirius.kernel.BaseSpecification
import sirius.kernel.di.std.Part

class ObjectStorageSpec extends BaseSpecification {

    @Part
    private static ObjectStorage storage

    def "fs engine stores and fetches data as expected"() {
        given:
        def testData = "test".getBytes(Charsets.UTF_8)
        when:
        storage.getSpace("fs-test").upload("test", new ByteArrayInputStream(testData), testData.length)
        and:
        def downloaded = storage.getSpace("fs-test").download("test")
        then:
        downloaded.isPresent()
        and:
        CharStreams.toString(new InputStreamReader(downloaded.get().getInputStream(), Charsets.UTF_8)) == "test"
    }

    def "fs engine deletes data as expected"() {
        given:
        def testData = "test".getBytes(Charsets.UTF_8)
        when:
        storage.getSpace("fs-test").upload("delete-test", new ByteArrayInputStream(testData), testData.length)
        and:
        def downloadedBeforeDelete = storage.getSpace("fs-test").download("delete-test")
        and:
        storage.getSpace("fs-test").delete("delete-test")
        and:
        def downloadedAfterDelete = storage.getSpace("fs-test").download("delete-test")
        then:
        downloadedBeforeDelete.isPresent()
        and:
        !downloadedAfterDelete.isPresent()
    }

    def "s3 engine stores and fetches data as expected"() {
        given:
        def testData = "test".getBytes(Charsets.UTF_8)
        when:
        storage.getSpace("s3-test").upload("test", new ByteArrayInputStream(testData), testData.length)
        and:
        def downloaded = storage.getSpace("s3-test").download("test")
        then:
        downloaded.isPresent()
        and:
        CharStreams.toString(new InputStreamReader(downloaded.get().getInputStream(), Charsets.UTF_8)) == "test"
    }

    def "s3 engine deletes data as expected"() {
        given:
        def testData = "test".getBytes(Charsets.UTF_8)
        when:
        storage.getSpace("s3-test").upload("delete-test", new ByteArrayInputStream(testData), testData.length)
        and:
        def downloadedBeforeDelete = storage.getSpace("s3-test").download("delete-test")
        and:
        storage.getSpace("s3-test").delete("delete-test")
        and:
        def downloadedAfterDelete = storage.getSpace("s3-test").download("delete-test")
        then:
        downloadedBeforeDelete.isPresent()
        and:
        !downloadedAfterDelete.isPresent()
    }

}
