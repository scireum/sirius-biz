/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.kb

import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import sirius.biz.tycho.kb.KnowledgeBaseArticle
import sirius.kernel.SiriusExtension
import java.net.URI
import kotlin.test.assertEquals

/**
 * Tests the editing interface of [KnowledgeBaseArticle].
 */
@ExtendWith(SiriusExtension::class)
class KbaEditLinkTest {
    @CsvSource(
        delimiter = '|', useHeadersInDisplayName = true, textBlock = """
        resource | github
        file:/Users/jakob/Development/memoio/target/classes/kb/de/api-manual/api-manual-FCJJ2.html.pasta | https://github.com/scireum/memoio/blob/-/src/main/resources/kb/de/api-manual/api-manual-FCJJ2.html.pasta
        file:/home/sirius/app/kb/de/integration-manual/deep-integration/iam-integration-JURIH.html.pasta | https://github.com/scireum/sirius-biz/blob/-/src/main/resources/kb/de/integration-manual/deep-integration/iam-integration-JURIH.html.pasta
        jar:file:/Users/jakob/.m2/repository/com/scireum/sirius-biz/DEVELOPMENT-SNAPSHOT/sirius-biz-DEVELOPMENT-SNAPSHOT.jar!/default/kb/en/system/kb-VFLEO.html.pasta | https://github.com/scireum/sirius-biz/blob/-/src/main/resources/default/kb/en/system/kb-VFLEO.html.pasta
        jar:file:/home/sirius/lib/sirius-biz-dev-120.7.0.jar!/default/kb/de/user/jobs-and-cron-92A2A.html.pasta | https://github.com/scireum/sirius-biz/blob/-/src/main/resources/default/kb/de/user/jobs-and-cron-92A2A.html.pasta"""
    )
    @ParameterizedTest
    fun `Github links are correctly extracted from resource paths`(resource: String, github: String) {
        assertEquals(
            github,
            KnowledgeBaseArticle.extractProjectAndPathFromResourceUrl(URI.create(resource).toURL()).toGithubUrl()
        )
    }
}
