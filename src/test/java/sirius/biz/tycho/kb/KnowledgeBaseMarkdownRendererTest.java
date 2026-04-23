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
    private static final String ARTICLE_PATH = "/kb/en/admin/markdown-QMDKB.md";

    @Test
    public void parseArticleMapsFrontmatterToKbMetadata() {
        KnowledgeBaseMarkdownArticle article = createArticle("Intro");

        assertEquals("QMDKB", article.articleId());
        assertEquals("en", article.language());
        assertEquals("Markdown Article", article.title());
        assertEquals("Short summary", article.description());
        assertEquals("SKAME", article.parentId());
        assertEquals(250, article.priority());
        assertEquals("flag-system-tenant", article.permissions());
        assertFalse(article.chapter());
        assertEquals(List.of("VFLEO", "DJELK"), article.crossReferences());
        assertTrue(renderer.renderDocument(article).sections().getFirst().html().contains("<p>Intro</p>"));
    }

    @Test
    public void renderDocumentBuildsSectionsAnchorsAndHtml() {
        KnowledgeBaseMarkdownDocument document = renderer.renderDocument(createArticle("""
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

    @Test
    public void documentExposesOnlyHeadedSectionsForTableOfContents() {
        KnowledgeBaseMarkdownDocument document = renderer.renderDocument(createArticle("""
                                                                                               Intro paragraph.
                                                                                               
                                                                                               ## Overview
                                                                                               
                                                                                               First section.
                                                                                               
                                                                                               ## Details
                                                                                               
                                                                                               Second section.
                                                                                               """));

        assertEquals(2, document.tableOfContentsSections().size());
        assertEquals("Overview", document.tableOfContentsSections().get(0).heading());
        assertEquals("overview", document.tableOfContentsSections().get(0).anchor());
        assertEquals("Details", document.tableOfContentsSections().get(1).heading());
        assertEquals("details", document.tableOfContentsSections().get(1).anchor());
    }

    @Test
    public void renderDocumentTurnsMarkdownTablesIntoKbStyledTables() {
        KnowledgeBaseMarkdownDocument document = renderer.renderDocument(createArticle("""
                                                                                               ## Response Codes
                                                                                               
                                                                                               | Code | Description |
                                                                                               | --- | --- |
                                                                                               | 200 | Everything works as expected. |
                                                                                               | 404 | Resource not found. |
                                                                                               """));

        String html = document.sections().getFirst().html();
        assertTrue(html.contains("<table class=\"table table-striped table-small-text\">"));
        assertTrue(html.contains("<thead>"));
        assertTrue(html.contains("<tbody>"));
        assertTrue(html.contains("<th>Code</th>"));
        assertTrue(html.contains("<td>404</td>"));
        assertTrue(html.contains("Resource not found."));
    }

    @Test
    public void renderDocumentTurnsNoteAlertsIntoInfoStyledSections() {
        KnowledgeBaseMarkdownDocument document = renderer.renderDocument(createArticle("""
                                                                                               ## Alerts
                                                                                               
                                                                                               > [!NOTE]
                                                                                               > Markdown KB articles can now render hint boxes.
                                                                                               """));

        String html = document.sections().getFirst().html();
        assertTrue(html.contains("card mb-4 full-border border-sirius-blue-light"));
        assertTrue(html.contains("card-title text-sirius-blue-light"));
        assertTrue(html.contains("fa-solid fa-info-circle"));
        assertTrue(html.contains(">TychoAlertNodeRenderer.type.NOTE</h5>"));
        assertTrue(html.contains("Markdown KB articles can now render hint boxes."));
    }

    @Test
    public void renderDocumentTurnsWarningAlertsIntoWarnStyledSections() {
        KnowledgeBaseMarkdownDocument document = renderer.renderDocument(createArticle("""
                                                                                               ## Alerts
                                                                                               
                                                                                               > [!WARNING]
                                                                                               > Please plan expensive jobs carefully.
                                                                                               """));

        String html = document.sections().getFirst().html();
        assertTrue(html.contains("card mb-4 full-border border-sirius-yellow-dark"));
        assertTrue(html.contains("card-title text-sirius-yellow-dark"));
        assertTrue(html.contains("fa-solid fa-exclamation-triangle"));
        assertTrue(html.contains(">TychoAlertNodeRenderer.type.WARNING</h5>"));
        assertTrue(html.contains("Please plan expensive jobs carefully."));
    }

    @Test
    public void renderDocumentLeavesNormalBlockquotesUntouched() {
        KnowledgeBaseMarkdownDocument document = renderer.renderDocument(createArticle("""
                                                                                               ## Quotes
                                                                                               
                                                                                               > This is a normal blockquote.
                                                                                               """));

        String html = document.sections().getFirst().html();
        assertTrue(html.contains("<blockquote>"));
        assertFalse(html.contains("border-sirius-blue-light"));
        assertFalse(html.contains("border-sirius-yellow-dark"));
    }

    @Test
    public void renderDocumentKeepsNestedMarkdownInsideAlerts() {
        KnowledgeBaseMarkdownDocument document = renderer.renderDocument(createArticle("""
                                                                                               ## Alerts
                                                                                               
                                                                                               > [!TIP]
                                                                                               > Prefer background processing for long-running work.
                                                                                               >
                                                                                               > - Use queues
                                                                                               > - Monitor cluster load
                                                                                               """));

        String html = document.sections().getFirst().html();
        assertTrue(html.contains(">TychoAlertNodeRenderer.type.TIP</h5>"));
        assertTrue(html.contains("<ul>"));
        assertTrue(html.contains("<li>Use queues</li>"));
        assertTrue(html.contains("<li>Monitor cluster load</li>"));
    }

    private KnowledgeBaseMarkdownArticle createArticle(String markdown) {
        return renderer.parseArticle(ARTICLE_PATH, """
                                                           ---
                                                           code: qmdkb
                                                           lang: en
                                                           title: Markdown Article
                                                           description: Short summary
                                                           parent: skame
                                                           priority: 250
                                                           permissions: flag-system-tenant
                                                           chapter: false
                                                           crossReferences:
                                                             - vfleo
                                                             - djelk
                                                           ---
                                                           """ + markdown);
    }
}
