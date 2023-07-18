/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class BlobUriParserTest {

    @ParameterizedTest
    @CsvSource(
            value = [
                "/dasd/p/SPACE/ACCESS_TOKEN/BLOB_KEY/PHYSICAL_KEY.ext,                false, false, PHYSICAL_KEY.ext",
                "/dasd/p/SPACE/ACCESS_TOKEN/BLOB_KEY/seo-text--PHYSICAL_KEY.ext,      false, false, PHYSICAL_KEY.ext",
                "/dasd/pd/SPACE/ACCESS_TOKEN/BLOB_KEY/PHYSICAL_KEY/seo--FILENAME.ext, false, true,  FILENAME.ext",
                "/dasd/xxl/pd/SPACE/ACCESS_TOKEN/BLOB_KEY/PHYSICAL_KEY/FILENAME.ext,  true,  true,  FILENAME.ext"
            ]
    )
    fun `parsing of physical blob URIs works`(
            uri: String,
            largeFileExpected: Boolean,
            download: Boolean,
            filename: String
    ) {
        val blobUri = BlobUriParser.parseBlobUri(uri).orElse(null)

        Assertions.assertEquals(blobUri.isLargeFileExpected, largeFileExpected)
        Assertions.assertEquals(blobUri.isPhysical, true)
        Assertions.assertEquals(blobUri.isDownload, download)
        Assertions.assertEquals(blobUri.isCacheable, false)
        Assertions.assertEquals(blobUri.storageSpace, "SPACE")
        Assertions.assertEquals(blobUri.accessToken, "ACCESS_TOKEN")
        Assertions.assertEquals(blobUri.blobKey, "BLOB_KEY")
        Assertions.assertEquals(blobUri.physicalKey, "PHYSICAL_KEY")
        Assertions.assertEquals(blobUri.variant, null)
        Assertions.assertEquals(blobUri.filename, filename)
    }

    @ParameterizedTest
    @CsvSource(
            value = [
                "/dasd/v/SPACE/ACCESS_TOKEN/VARIANT/BLOB_KEY.ext,                     false, false, false, BLOB_KEY.ext",
                "/dasd/cv/SPACE/ACCESS_TOKEN/VARIANT/seo-text--BLOB_KEY.ext,          false, false, true,  BLOB_KEY.ext",
                "/dasd/cv/SPACE/ACCESS_TOKEN/VARIANT/BLOB_KEY/seo-text--FILENAME.ext, false, false, true,  FILENAME.ext",
                "/dasd/cvd/SPACE/ACCESS_TOKEN/VARIANT/BLOB_KEY/FILENAME.ext,          false, true,  true,  FILENAME.ext",
                "/dasd/xxl/cvd/SPACE/ACCESS_TOKEN/VARIANT/BLOB_KEY/FILENAME.ext,      true,  true,  true,  FILENAME.ext"
            ]
    )
    fun `parsing of virtual blob URIs works`(
            uri: String,
            largeFileExpected: Boolean,
            download: Boolean,
            cacheable: Boolean,
            filename: String
    ) {
        val blobUri = BlobUriParser.parseBlobUri(uri).orElse(null)

        Assertions.assertEquals(blobUri.isLargeFileExpected, largeFileExpected)
        Assertions.assertEquals(blobUri.isPhysical, false)
        Assertions.assertEquals(blobUri.isDownload, download)
        Assertions.assertEquals(blobUri.isCacheable, cacheable)
        Assertions.assertEquals(blobUri.storageSpace, "SPACE")
        Assertions.assertEquals(blobUri.accessToken, "ACCESS_TOKEN")
        Assertions.assertEquals(blobUri.blobKey, "BLOB_KEY")
        Assertions.assertEquals(blobUri.physicalKey, null)
        Assertions.assertEquals(blobUri.variant, "VARIANT")
        Assertions.assertEquals(blobUri.filename, filename)
    }
}
