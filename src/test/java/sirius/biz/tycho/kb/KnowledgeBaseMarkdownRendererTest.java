/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.kb;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/// Tests the [KnowledgeBaseMarkdownRenderer] and verifies that markdown articles are correctly rendered.
public class KnowledgeBaseMarkdownRendererTest {

    private final KnowledgeBaseMarkdownRenderer renderer = new KnowledgeBaseMarkdownRenderer();

    @Test
    public void parseArticleMapsFrontmatterToKbMetadata() {
        KnowledgeBaseMarkdownArticle article = renderer.parseArticle("/kb/en/admin/markdown-QMDKB.md", """
                ---
                code: qmdkb
                lang: en
                title: Markdown Article
                description: Short summary
                parent: skame
                priority: 250
                permissions: flag-system-tenant
                chapter: true
                crossReferences:
                  - vfleo
                  - djelk
                ---
                Intro
                """);

        assertEquals("QMDKB", article.articleId());
        assertEquals("en", article.language());
        assertEquals("Markdown Article", article.title());
        assertEquals("Short summary", article.description());
        assertEquals("SKAME", article.parentId());
        assertEquals(250, article.priority());
        assertEquals("flag-system-tenant", article.permissions());
        assertTrue(article.chapter());
        assertEquals(List.of("VFLEO", "DJELK"), article.crossReferences());
        assertEquals("Intro", article.markdownBody());
    }

    @Test
    public void renderDocumentBuildsSectionsAnchorsAndHtml() {
        KnowledgeBaseMarkdownDocument document = renderer.renderDocument(new KnowledgeBaseMarkdownArticle(
                "/kb/en/admin/markdown-QMDKB.md",
                "QMDKB",
                "en",
                "Markdown Article",
                "Short summary",
                "SKAME",
                100,
                "",
                false,
                List.of(),
                """
                        Intro paragraph with a [link](https://example.com).
                        
                        ## Section
                        
                        ![Diagram](/kb/assets/example.svg)
                        
                        ```java
                        String value = "test";
                        ```
                        
                        ## Section
                        
                        Closing paragraph.
                        """));

        assertEquals(3, document.sections().size());
        assertFalse(document.sections().getFirst().html().isEmpty());
        assertEquals("Section", document.sections().get(1).heading());
        assertEquals("section", document.sections().get(1).anchor());
        assertTrue(document.sections().get(1).html().contains("href=\"https://example.com\"") || document.sections()
                                                                                                         .getFirst()
                                                                                                         .html()
                                                                                                         .contains(
                                                                                                                 "href=\"https://example.com\""));
        assertTrue(document.sections().get(1).html().contains("src=\"/kb/assets/example.svg\""));
        assertTrue(document.sections().get(1).html().contains("prettyprint p-2 lang-java"));
        assertEquals("section-2", document.sections().get(2).anchor());
        assertTrue(document.hasTableOfContents());
    }
}
