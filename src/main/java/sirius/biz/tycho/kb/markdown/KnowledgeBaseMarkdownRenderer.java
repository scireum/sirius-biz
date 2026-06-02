/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.kb.markdown;

import org.commonmark.Extension;
import org.commonmark.ext.front.matter.YamlFrontMatterExtension;
import org.commonmark.ext.front.matter.YamlFrontMatterVisitor;
import org.commonmark.ext.gfm.alerts.AlertsExtension;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.AttributeProvider;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.text.TextContentRenderer;
import sirius.kernel.commons.Streams;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.resources.Resource;
import sirius.web.resources.Resources;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Loads and renders Markdown-based KB articles.
 */
@Register(classes = KnowledgeBaseMarkdownRenderer.class)
public class KnowledgeBaseMarkdownRenderer {

    private static final String KB_TABLE_CLASSES = "table table-striped table-small-text";

    @Part
    private Resources resources;

    private static final List<Extension> PARSER_EXTENSIONS = List.of(TablesExtension.create(),
                                                                     AlertsExtension.create(),
                                                                     YamlFrontMatterExtension.create(),
                                                                     ArticleReferenceParserExtension.create());
    private static final List<Extension> HTML_EXTENSIONS = List.of(TablesExtension.create());
    private final Parser parser = Parser.builder().extensions(PARSER_EXTENSIONS).build();
    private final HtmlRenderer htmlRenderer = HtmlRenderer.builder()
                                                          .escapeHtml(true)
                                                          .sanitizeUrls(true)
                                                          .extensions(HTML_EXTENSIONS)
                                                          .attributeProviderFactory(context -> new KbAttributeProvider())
                                                          .nodeRendererFactory(AlertNodeRenderer::new)
                                                          .nodeRendererFactory(FencedCodeBlockNodeRenderer::new)
                                                          .nodeRendererFactory(PreviewImageNodeRenderer::new)
                                                          .nodeRendererFactory(ArticleReferenceNodeRenderer::new)
                                                          .build();
    private final TextContentRenderer textRenderer = TextContentRenderer.builder().build();

    /**
     * Loads and parses a Markdown knowledge base article from the given resource path.
     *
     * @param resourcePath the classpath resource path of the Markdown file
     * @return the parsed article, or an empty optional if the resource does not exist
     */
    public Optional<KnowledgeBaseMarkdownArticle> loadArticle(String resourcePath) {
        return resources.resolve(resourcePath)
                        .map(this::readResource)
                        .map(content -> parseArticle(resourcePath, content));
    }

    /**
     * Loads, parses, and renders a Markdown knowledge base article into a structured document.
     *
     * @param resourcePath the classpath resource path of the Markdown file
     * @return the rendered document containing the sectioned HTML output
     * @throws IllegalArgumentException if the resource does not exist
     */
    public KnowledgeBaseMarkdownDocument renderDocument(String resourcePath) {
        return loadArticle(resourcePath).map(this::renderDocument)
                                        .orElseThrow(() -> new IllegalArgumentException("Missing Markdown KBA: "
                                                                                        + resourcePath));
    }

    KnowledgeBaseMarkdownArticle parseArticle(String resourcePath, String content) {
        String normalizedContent = content.replace("\r\n", "\n");
        Node document = parser.parse(normalizedContent);

        // Extract front matter
        YamlFrontMatterVisitor visitor = new YamlFrontMatterVisitor();
        document.accept(visitor);
        Map<String, List<String>> metadata = visitor.getData();

        return new KnowledgeBaseMarkdownArticle(resourcePath,
                                                requireString(metadata, "code").toUpperCase(Locale.ENGLISH),
                                                requireString(metadata, "lang"),
                                                requireString(metadata, "title"),
                                                firstValue(metadata, "description"),
                                                firstValue(metadata, "parent").toUpperCase(Locale.ENGLISH),
                                                Value.of(firstValue(metadata, "priority")).asInt(100),
                                                firstValue(metadata, "permissions"),
                                                Value.of(firstValue(metadata, "chapter")).asBoolean(),
                                                resolveStringList(metadata.get("crossReferences")),
                                                document);
    }

    KnowledgeBaseMarkdownDocument renderDocument(KnowledgeBaseMarkdownArticle article) {
        List<KnowledgeBaseMarkdownSection> sections = new ArrayList<>();
        Map<String, Integer> anchors = new LinkedHashMap<>();

        String currentHeading = "";
        String currentAnchor = "";
        StringBuilder currentHtml = new StringBuilder();

        for (Node node = article.markdown().getFirstChild(); node != null; node = node.getNext()) {
            if (isSectionHeading(node)) {
                appendSection(sections, currentHeading, currentAnchor, currentHtml);

                currentHeading = textRenderer.render(node).strip();
                currentAnchor = createAnchor(currentHeading, anchors);
                currentHtml = new StringBuilder();
                continue;
            }

            currentHtml.append(htmlRenderer.render(node));
        }

        appendSection(sections, currentHeading, currentAnchor, currentHtml);
        if (sections.isEmpty()) {
            sections.add(new KnowledgeBaseMarkdownSection("", "", ""));
        }

        return new KnowledgeBaseMarkdownDocument(sections);
    }

    private boolean isSectionHeading(Node node) {
        return node instanceof Heading heading && heading.getLevel() <= 2;
    }

    private void appendSection(List<KnowledgeBaseMarkdownSection> sections,
                               String heading,
                               String anchor,
                               StringBuilder html) {
        String renderedHtml = html.toString().strip();
        if (Strings.isFilled(heading) || Strings.isFilled(renderedHtml)) {
            sections.add(new KnowledgeBaseMarkdownSection(heading,
                                                          Strings.isFilled(heading) ? anchor : "",
                                                          renderedHtml));
        }
    }

    private String createAnchor(String heading, Map<String, Integer> knownAnchors) {
        String normalized = heading.toLowerCase(Locale.ENGLISH).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (Strings.isEmpty(normalized)) {
            normalized = "section";
        }

        int count = knownAnchors.merge(normalized, 1, Integer::sum);
        return count == 1 ? normalized : normalized + "-" + count;
    }

    private String readResource(Resource resource) {
        try (InputStreamReader reader = new InputStreamReader(resource.openStream(), StandardCharsets.UTF_8)) {
            return Streams.readToString(reader);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Failed to read Markdown KBA: " + resource.getPath(), exception);
        }
    }

    private String requireString(Map<String, List<String>> metadata, String key) {
        String value = firstValue(metadata, key);
        if (Strings.isEmpty(value)) {
            throw new IllegalArgumentException("Markdown KBA frontmatter is missing '" + key + "'.");
        }
        return value;
    }

    private String firstValue(Map<String, List<String>> metadata, String key) {
        return Value.of(metadata.getOrDefault(key, List.of()).stream().findFirst().orElse("")).trim();
    }

    private List<String> resolveStringList(List<String> rawValues) {
        if (rawValues == null || rawValues.isEmpty()) {
            return List.of();
        }

        return rawValues.stream()
                        .flatMap(value -> Arrays.stream(value.split(",")))
                        .map(String::trim)
                        .map(s -> s.toUpperCase(Locale.ENGLISH))
                        .filter(Strings::isFilled)
                        .toList();
    }

    private static final class KbAttributeProvider implements AttributeProvider {

        @Override
        public void setAttributes(Node node, String tagName, Map<String, String> attributes) {
            if (node instanceof TableBlock) {
                attributes.put("class", KB_TABLE_CLASSES);
            }
        }
    }
}
