/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import sirius.kernel.SiriusExtension
import sirius.kernel.di.std.Part
import java.net.URI
import java.util.*
import java.util.stream.Stream

@ExtendWith(SiriusExtension::class)
class VirtualFileTest {

    companion object {
        @JvmStatic
        fun resolveTestData(): Stream<Arguments> {
            return Stream.of(
                    Arguments.of(
                            "https://example.com/foo/bar.jpg",
                            "/foo/bar.jpg",
                            EnumSet.noneOf(RemoteFileResolver.Options::class.java)
                    ),
                    Arguments.of(
                            "https://example.com/foo.aspx?x=bar.jpg",
                            "/bar.jpg",
                            EnumSet.noneOf(RemoteFileResolver.Options::class.java)
                    ),
                    Arguments.of(
                            "https://example.com/foo.aspx?x=test.png&y=foo%2Fbar.jpg",
                            "/foo/bar.jpg",
                            EnumSet.noneOf(RemoteFileResolver.Options::class.java)
                    ),
                    Arguments.of(
                            "https://www.example.com/foo/bar.jpg",
                            "/example.com/foo/bar.jpg",
                            EnumSet.of(RemoteFileResolver.Options.INCLUDE_HOST_NAME)
                    )
            )
        }

        @Part
        private var vfs: VirtualFileSystem? = null
    }

    @ParameterizedTest
    @MethodSource("resolveTestData")
    fun `URIs are properly resolved into VirtualFiles`(
            uri: URI,
            path: String,
            options: Set<RemoteFileResolver.Options>
    ) {
        val virtualFile =
                vfs!!.root().resolveOrLoadChildFromURL(uri, FetchFromUrlMode.NEVER_FETCH, "jpg"::equals, options).first

        Assertions.assertEquals(virtualFile.path(), path)
    }
}
