/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer1

import sirius.kernel.BaseSpecification
import sirius.kernel.commons.Streams
import sirius.kernel.di.std.Part

import java.nio.charset.StandardCharsets
import java.nio.file.Files

class ObjectStorageSpec extends BaseSpecification {

    @Part
    private static ObjectStorage storage

    private static byte[] generateRandomData(int length) {
        Random rnd = new Random()
        byte[] result = new byte[length]
        rnd.nextBytes(result)
        return result
    }

    def "storing and fetching data works as expected"(int length, String space) {
        given:
        def testData = generateRandomData(length)
        def objectName = "test-data-" + length
        def objectSpace = storage.getSpace(space)
        when:
        objectSpace.upload(objectName, new ByteArrayInputStream(testData), testData.length)
        and:
        def downloaded = objectSpace.download(objectName)
        then:
        downloaded.isPresent()
        and:
        Files.readAllBytes(downloaded.get().getFile().toPath()) == testData
        and:
        Streams.toByteArray(objectSpace.getInputStream(objectName).get()) == testData
        where:
        length | space
        128    | "fs-test"
        4096   | "fs-test"
        8192   | "fs-test"
        10000  | "fs-test"
        16384  | "fs-test"
        128    | "fs-zip-test"
        4096   | "fs-zip-test"
        8192   | "fs-zip-test"
        10000  | "fs-zip-test"
        16384  | "fs-zip-test"
        128    | "fs-aes-test"
        4096   | "fs-aes-test"
        8192   | "fs-aes-test"
        10000  | "fs-aes-test"
        16384  | "fs-aes-test"
        128    | "fs-zip-aes-test"
        4096   | "fs-zip-aes-test"
        8192   | "fs-zip-aes-test"
        10000  | "fs-zip-aes-test"
        16384  | "fs-zip-aes-test"
        128    | "s3-test"
        4096   | "s3-test"
        8192   | "s3-test"
        10000  | "s3-test"
        16384  | "s3-test"
        128    | "s3-zip-test"
        4096   | "s3-zip-test"
        8192   | "s3-zip-test"
        10000  | "s3-zip-test"
        16384  | "s3-zip-test"
        128    | "s3-aes-test"
        4096   | "s3-aes-test"
        8192   | "s3-aes-test"
        10000  | "s3-aes-test"
        16384  | "s3-aes-test"
        128    | "s3-zip-aes-test"
        4096   | "s3-zip-aes-test"
        8192   | "s3-zip-aes-test"
        10000  | "s3-zip-aes-test"
        16384  | "s3-zip-aes-test"
    }

    def "deleting data works as expected"(String space) {
        given:
        def testData = "test".getBytes(StandardCharsets.UTF_8)
        def objectSpace = storage.getSpace(space)
        when:
        objectSpace.upload("delete-test", new ByteArrayInputStream(testData), testData.length)
        and:
        def downloadedBeforeDelete = objectSpace.download("delete-test")
        and:
        objectSpace.delete("delete-test")
        and:
        def downloadedAfterDelete = objectSpace.download("delete-test")
        then:
        downloadedBeforeDelete.isPresent()
        and:
        !downloadedAfterDelete.isPresent()
        where:
        space     | _
        "fs-test" | _
        "s3-test" | _
    }

}
