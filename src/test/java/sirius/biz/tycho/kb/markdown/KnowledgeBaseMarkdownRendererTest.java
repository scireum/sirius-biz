/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.kb.markdown;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import sirius.kernel.SiriusExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/// Tests the [KnowledgeBaseMarkdownRenderer] and verifies that markdown articles are correctly rendered.
@ExtendWith(SiriusExtension.class)
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
        String articleMarkdown = """
                Intro paragraph with a [link](https://example.com).
                
                ## Section
                
                ![Diagram](/kb/assets/example.svg)
                
                ```java
                String value = "test";
                ```
                
                ## Section
                
                Closing paragraph.
                """;
        KnowledgeBaseMarkdownDocument document = renderer.renderDocument(createArticle(articleMarkdown));

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
    public void renderDocumentTurnsStandaloneMarkdownImagesIntoPreviewImages() {
        String articleMarkdown = """
                ## Images
                
                ![Markdown example image](/kb/assets/markdown-example.svg)
                """;
        KnowledgeBaseMarkdownDocument document = renderer.renderDocument(createArticle(articleMarkdown));

        String html = document.sections().getFirst().html();
        assertFalse(html.contains("<p><div"));
        assertTrue(html.contains("onclick=\"openImageOverlay('/kb/assets/markdown-example.svg')\""));
        assertTrue(html.contains("src=\"/kb/assets/markdown-example.svg\""));
        assertTrue(html.contains(
                "class=\"img-fluid cursor-pointer sci-kba-preview-image card card-hover-shadow card-border\""));
        assertTrue(html.contains(">Markdown example image</p>"));
        assertTrue(html.contains("sci-kba-image-overlay"));
    }

    @Test
    public void renderDocumentRendersFencedCodeBlocksThroughCodeTag() {
        String articleMarkdown = """
                ## Code
                
                ```java
                if (left < right) {
                    return "ok";
                }
                ```
                """;
        KnowledgeBaseMarkdownDocument document = renderer.renderDocument(createArticle(articleMarkdown));

        String html = document.sections().getFirst().html();
        assertTrue(html.contains("<pre class=\"prettyprint p-2 lang-java\">"));
        assertTrue(html.contains("if (left &lt; right)"));
        assertFalse(html.contains("<code>"));
        assertFalse(html.contains("language-java"));
    }

    @Test
    public void renderDocumentEscapesMermaidFencedCodeBlocks() {
        String articleMarkdown = """
                ## Diagram
                
                ```mermaid
                flowchart TD
                    A["<img src=x onerror=alert(1)>"] --> B
                ```
                """;
        KnowledgeBaseMarkdownDocument document = renderer.renderDocument(createArticle(articleMarkdown));

        String html = document.sections().getFirst().html();
        assertTrue(html.contains("class=\"mermaid kb-diagram"));
        assertFalse(html.contains("<img src=x onerror=alert(1)>"));
        assertTrue(html.contains("&lt;img src=x onerror=alert(1)&gt;"));
    }

    @Test
    public void renderDocumentRendersInlineCodeAndLinksThroughTagliatelleTags() {
        String articleMarkdown = """
                ## Inline Elements
                
                Use `a < b` and open [Tagliatelle Tag Overview](https://example.com/tags).
                """;
        KnowledgeBaseMarkdownDocument document = renderer.renderDocument(createArticle(articleMarkdown));

        String html = document.sections().getFirst().html();
        assertTrue(html.contains("<span class=\"kb-inline-code\">a &lt; b</span>"));
        assertFalse(html.contains("<code>"));
        assertTrue(html.contains("href=\"https://example.com/tags\""));
        assertTrue(html.contains("fa-solid fa-external-link-alt"));
        assertTrue(html.contains("<span class=\"text-decoration-underline\">Tagliatelle Tag Overview</span>"));
    }

    @Test
    public void renderDocumentDoesNotCreateEmptySectionForFrontmatterOnlyArticles() {
        String articleMarkdown = "";
        KnowledgeBaseMarkdownDocument document = renderer.renderDocument(createArticle(articleMarkdown));

        assertTrue(document.sections().isEmpty());
        assertFalse(document.hasTableOfContents());
    }

    @Test
    public void documentExposesOnlyHeadedSectionsForTableOfContents() {
        String articleMarkdown = """
                Intro paragraph.
                
                ## Overview
                
                First section.
                
                ## Details
                
                Second section.
                """;
        KnowledgeBaseMarkdownDocument document = renderer.renderDocument(createArticle(articleMarkdown));

        assertEquals(2, document.tableOfContentsSections().size());
        assertEquals("Overview", document.tableOfContentsSections().get(0).heading());
        assertEquals("overview", document.tableOfContentsSections().get(0).anchor());
        assertEquals("Details", document.tableOfContentsSections().get(1).heading());
        assertEquals("details", document.tableOfContentsSections().get(1).anchor());
    }

    @Test
    public void renderDocumentTurnsMarkdownTablesIntoKbStyledTables() {
        String articleMarkdown = """
                ## Response Codes
                
                | Code | Description |
                | --- | --- |
                | 200 | Everything works as expected. |
                | 404 | Resource not found. |
                """;
        KnowledgeBaseMarkdownDocument document = renderer.renderDocument(createArticle(articleMarkdown));

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
        String articleMarkdown = """
                ## Alerts
                
                > [!NOTE]
                > Markdown KB articles can now render hint boxes.
                """;
        KnowledgeBaseMarkdownDocument document = renderer.renderDocument(createArticle(articleMarkdown));

        String html = document.sections().getFirst().html();
        assertTrue(html.contains("card mb-4 full-border border-sirius-blue-light"));
        assertTrue(html.contains("card-title text-sirius-blue-light"));
        assertTrue(html.contains("fa-solid fa-info-circle"));
        assertTrue(html.contains("Markdown KB articles can now render hint boxes."));
    }

    @Test
    public void renderDocumentTurnsWarningAlertsIntoWarnStyledSections() {
        String articleMarkdown = """
                ## Alerts
                
                > [!WARNING]
                > Please plan expensive jobs carefully.
                """;
        KnowledgeBaseMarkdownDocument document = renderer.renderDocument(createArticle(articleMarkdown));

        String html = document.sections().getFirst().html();
        assertTrue(html.contains("card mb-4 full-border border-sirius-yellow-dark"));
        assertTrue(html.contains("card-title text-sirius-yellow-dark"));
        assertTrue(html.contains("fa-solid fa-exclamation-triangle"));
        assertTrue(html.contains("Please plan expensive jobs carefully."));
    }

    @Test
    public void renderDocumentLeavesNormalBlockquotesUntouched() {
        String articleMarkdown = """
                ## Quotes
                
                > This is a normal blockquote.
                """;
        KnowledgeBaseMarkdownDocument document = renderer.renderDocument(createArticle(articleMarkdown));

        String html = document.sections().getFirst().html();
        assertTrue(html.contains("<blockquote>"));
        assertFalse(html.contains("border-sirius-blue-light"));
        assertFalse(html.contains("border-sirius-yellow-dark"));
    }

    @Test
    public void renderDocumentKeepsNestedMarkdownInsideAlerts() {
        String articleMarkdown = """
                ## Alerts
                
                > [!TIP]
                > Prefer background processing for long-running work.
                >
                > - Use queues
                > - Monitor cluster load
                """;
        KnowledgeBaseMarkdownDocument document = renderer.renderDocument(createArticle(articleMarkdown));

        String html = document.sections().getFirst().html();
        assertTrue(html.contains("card mb-4 full-border border-sirius-green-light"));
        assertTrue(html.contains("fa-solid fa-lightbulb"));
        assertTrue(html.contains("<ul>"));
        assertTrue(html.contains("<li>Use queues</li>"));
        assertTrue(html.contains("<li>Monitor cluster load</li>"));
    }

    @Test
    public void renderDocumentUsesCustomAlertTitleWithInlineFormatting() {
        String articleMarkdown = """
                ## Alerts
                
                > [!NOTE] Keep in **mind**
                > Custom titles override the default heading.
                """;
        KnowledgeBaseMarkdownDocument document = renderer.renderDocument(createArticle(articleMarkdown));

        String html = document.sections().getFirst().html();
        assertTrue(html.contains("card-title text-sirius-blue-light"));
        assertTrue(html.contains("fa-solid fa-info-circle"));
        String titleHtml = "Keep in <strong>mind</strong>";
        // The custom title replaces the default heading and keeps its inline formatting.
        assertTrue(html.contains(titleHtml));
        // Ensure the title is rendered only once (and therefore doesn't leak into the body).
        assertEquals(html.indexOf(titleHtml), html.lastIndexOf(titleHtml));
        assertTrue(html.contains("Custom titles override the default heading."));
    }

    @Test
    public void renderDocumentRendersAngleBracketKbaReferenceThroughRefTag() {
        String articleMarkdown = """
                ## References
                
                <kba:VFLEO>
                """;
        KnowledgeBaseMarkdownDocument document = renderer.renderDocument(createArticle(articleMarkdown));

        String html = document.sections().getFirst().html();
        assertTrue(html.contains("class=\"text-danger\""));
        assertTrue(html.contains("fa-solid fa-triangle-exclamation"));
    }

    @Test
    public void renderDocumentRendersMarkdownKbaReferenceWithCustomLabelThroughRefTag() {
        String articleMarkdown = """
                ## References
                
                [Artikel zum Thema X](kba:VFLEO#Basics)
                """;
        KnowledgeBaseMarkdownDocument document = renderer.renderDocument(createArticle(articleMarkdown));

        String html = document.sections().getFirst().html();
        assertTrue(html.contains("class=\"text-danger\""));
        assertTrue(html.contains("fa-solid fa-triangle-exclamation"));
        assertTrue(html.contains("<i>Artikel zum Thema X</i>"));
    }

    @Test
    public void renderDocumentKeepsInlineCodeInCustomKbaReferenceLabel() {
        String articleMarkdown = """
                ## References
                
                [`foo`](kba:VFLEO)
                """;
        KnowledgeBaseMarkdownDocument document = renderer.renderDocument(createArticle(articleMarkdown));

        String html = document.sections().getFirst().html();
        assertTrue(html.contains("class=\"text-danger\""));
        assertTrue(html.contains("<i>foo</i>"));
    }

    @Test
    public void renderDocumentRendersAngleBracketKbaReferenceWithAnchorThroughRefTag() {
        String articleMarkdown = """
                ## References
                
                <kba:VFLEO#Basics>
                """;
        KnowledgeBaseMarkdownDocument document = renderer.renderDocument(createArticle(articleMarkdown));

        String html = document.sections().getFirst().html();
        assertTrue(html.contains("class=\"text-danger\""));
        assertTrue(html.contains("fa-solid fa-triangle-exclamation"));
    }

    @Test
    public void renderDocumentShowsWarningStyleForUnresolvedKbaReferenceWithCustomLabel() {
        String articleMarkdown = """
                ## References
                
                [Einleitung im Artikel zum Thema X](kba:VFLEO#Basics)
                """;
        KnowledgeBaseMarkdownDocument document = renderer.renderDocument(createArticle(articleMarkdown));

        String html = document.sections().getFirst().html();
        assertFalse(html.contains("href=\"/kba/"));
        assertTrue(html.contains("class=\"text-danger\""));
        assertTrue(html.contains("fa-triangle-exclamation"));
        assertTrue(html.contains("Einleitung im Artikel zum Thema X"));
    }

    @Test
    public void renderDocumentEscapesCustomKbaReferenceLabelBeforeCallingRefTag() {
        String articleMarkdown = """
                ## References
                
                [<script>alert('x')</script>](kba:VFLEO)
                """;
        KnowledgeBaseMarkdownDocument document = renderer.renderDocument(createArticle(articleMarkdown));

        String html = document.sections().getFirst().html();
        assertFalse(html.contains("<script>alert"));
        assertTrue(html.contains("alert(&amp;#039;x&amp;#039;)"));
    }

    private KnowledgeBaseMarkdownArticle createArticle(String markdown) {
        String articleFrontmatter = """
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
                """;
        return renderer.parseArticle(ARTICLE_PATH, articleFrontmatter + markdown);
    }
}
