/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.kb

import io.netty.handler.codec.http.HttpResponseStatus
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import sirius.db.es.Elastic
import sirius.kernel.SiriusExtension
import sirius.kernel.di.Injector
import sirius.kernel.di.std.Part
import sirius.kernel.timer.EndOfDayTask
import sirius.pasta.tagliatelle.Tagliatelle
import sirius.web.http.TestRequest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests that the routes of the [KnowledgeBaseController] cannot be used to reach resources outside of the knowledge
 * base.
 *
 * Note that a route parameter may contain an encoded slash, which is only decoded once the route has matched.
 */
@ExtendWith(SiriusExtension::class)
class KnowledgeBaseControllerTest {
    @Test
    fun `the entry point of the knowledge base is rendered`() {
        val response = TestRequest.GET("/kb/en").execute()

        assertEquals(HttpResponseStatus.OK, response.status)
        assertFalse { response.contentAsString.contains(TEMPLATE_MARKER) }
    }

    @ValueSource(
        strings = [
            "/kb/x%2f..%2f..%2f..%2f..%2fkb-traversal-test",
            "/kba/x%2f..%2f..%2f..%2f..%2fkb-traversal-test/00000"]
    )
    @ParameterizedTest
    fun `the language cannot be used to render templates outside of the knowledge base`(uri: String) {
        // Ensures that the template exists, as its marker would otherwise be absent for the wrong reason
        assertTrue { tagliatelle.resolve("/templates/kb-traversal-test.html.pasta").isPresent }

        val response = TestRequest.GET(uri).execute()

        assertFalse { response.contentAsString.contains(TEMPLATE_MARKER) }
    }

    @Test
    fun `an image of the knowledge base is delivered`() {
        val response = TestRequest.GET("/kb/de/user/images/jobs-general.png").execute()

        assertEquals(HttpResponseStatus.OK, response.status)
        assertTrue { response.rawContent?.decodeToString().orEmpty().contains("PNG") }
    }

    @ValueSource(
        strings = [
            "/kb/..%2fassets%2fimages%2flogin_logo.png",
            "/kb/../assets/images/login_logo.png",
            "/kb/en/..%2f..%2fassets%2fimages%2flogin_logo.png"]
    )
    @ParameterizedTest
    fun `assets outside of the knowledge base are not delivered`(uri: String) {
        val response = TestRequest.GET(uri).execute()

        // Some of these URIs match the route of a language instead, which renders the entry point as HTML
        assertFalse { response.rawContent?.decodeToString().orEmpty().contains("PNG") }
    }

    companion object {
        private const val TEMPLATE_MARKER = "KB-TRAVERSAL-MARKER"

        /**
         * Fills the knowledge base, which is otherwise only done by a nightly task.
         */
        @JvmStatic
        @BeforeAll
        fun synchronizeArticles() {
            Injector.context().getParts(EndOfDayTask::class.java).filterIsInstance<SynchronizeArticlesTask>().single()
                .execute()
            elastic.refresh(KnowledgeBaseEntry::class.java)
            assertTrue { knowledgeBase.resolve("en", KnowledgeBase.ROOT_CHAPTER_ID, true).isPresent }
        }

        @JvmStatic
        @Part
        private lateinit var tagliatelle: Tagliatelle

        @JvmStatic
        @Part
        private lateinit var knowledgeBase: KnowledgeBase

        @JvmStatic
        @Part
        private lateinit var elastic: Elastic
    }
}
