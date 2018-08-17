/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.s3

import com.google.common.base.Charsets
import com.google.common.io.Files
import sirius.kernel.BaseSpecification
import sirius.kernel.di.std.Part

class ObjectStoresSpec extends BaseSpecification {

    @Part
    private static ObjectStores stores

    def "createBucketWorks"() {
        File download
        when:
        File file = File.createTempFile("test", "")
        Files.write("This is a test.", file, Charsets.UTF_8)
        and:
        stores.store().upload(stores.store().getBucketName("test"), "test", file, null)
        and:
        download = stores.store().download(stores.store().getBucketName("test"), "test")
        then:
        Files.toString(file, Charsets.UTF_8) == Files.toString(download, Charsets.UTF_8)
        cleanup:
        sirius.kernel.commons.Files.delete(file)
        sirius.kernel.commons.Files.delete((File)download)
    }

}
