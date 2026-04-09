/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.kb;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.AttributeProvider;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.text.TextContentRenderer;
import org.yaml.snakeyaml.Yaml;
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
import java.util.regex.Pattern;

/**
 * Loads and renders Markdown-based KB articles.
 */
@Register(classes = KnowledgeBaseMarkdownRenderer.class)
public class KnowledgeBaseMarkdownRenderer {

    static final String FRONTMATTER_SEPARATOR = "---";

    private static final Pattern FENCED_CODE_WITH_LANGUAGE =
            Pattern.compile("<pre><code class=\"language-([^\"]+)\">(.*?)</code></pre>", Pattern.DOTALL);
    private static final Pattern FENCED_CODE = Pattern.compile("<pre><code>(.*?)</code></pre>", Pattern.DOTALL);
    private static final String KB_TABLE_CLASSES = "table table-striped table-small-text";

    @Part
    private Resources resources;

    private final List<Extension> markdownExtensions = List.of(TablesExtension.create());
    private final Parser parser = Parser.builder().extensions(markdownExtensions).build();
    private final HtmlRenderer htmlRenderer = HtmlRenderer.builder()
                                                          .escapeHtml(true)
                                                          .sanitizeUrls(true)
                                                          .extensions(markdownExtensions)
                                                          .attributeProviderFactory(context -> new KbAttributeProvider())
                                                          .build();
    private final TextContentRenderer textRenderer = TextContentRenderer.builder().build();
    private final Yaml yaml = new Yaml();

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
        Frontmatter frontmatter = extractFrontmatter(content);
        Map<String, Object> metadata = frontmatter.metadata();

        return new KnowledgeBaseMarkdownArticle(resourcePath,
                                                requireString(metadata, "code").toUpperCase(Locale.ENGLISH),
                                                requireString(metadata, "lang"),
                                                requireString(metadata, "title"),
                                                Value.of(metadata.get("description")).asString(""),
                                                Value.of(metadata.get("parent"))
                                                     .asString("")
                                                     .toUpperCase(Locale.ENGLISH),
                                                Value.of(metadata.get("priority")).asInt(100),
                                                Value.of(metadata.get("permissions")).asString(""),
                                                Value.of(metadata.get("chapter")).asBoolean(),
                                                resolveStringList(metadata.get("crossReferences")),
                                                frontmatter.body().strip());
    }

    KnowledgeBaseMarkdownDocument renderDocument(KnowledgeBaseMarkdownArticle article) {
        Node document = parser.parse(article.markdownBody());
        List<KnowledgeBaseMarkdownSection> sections = new ArrayList<>();
        Map<String, Integer> anchors = new LinkedHashMap<>();

        String currentHeading = "";
        String currentAnchor = "";
        StringBuilder currentHtml = new StringBuilder();

        for (Node node = document.getFirstChild(); node != null; node = node.getNext()) {
            if (isSectionHeading(node)) {
                appendSection(sections, currentHeading, currentAnchor, currentHtml);

                currentHeading = textRenderer.render(node).strip();
                currentAnchor = createAnchor(currentHeading, anchors);
                currentHtml = new StringBuilder();
                continue;
            }

            currentHtml.append(renderHtml(node));
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

    private String renderHtml(Node node) {
        String html = htmlRenderer.render(node);
        if (node instanceof FencedCodeBlock fencedCodeBlock && Strings.areEqual(fencedCodeBlock.getInfo(), "mermaid")) {
            return "<div class=\"mermaid kb-diagram\">" + fencedCodeBlock.getLiteral() + "</div>";
        }

        html = FENCED_CODE_WITH_LANGUAGE.matcher(html)
                                        .replaceAll("<pre class=\"prettyprint p-2 lang-$1\"><code>$2</code></pre>");
        return FENCED_CODE.matcher(html).replaceAll("<pre class=\"prettyprint p-2\"><code>$1</code></pre>");
    }

    private String createAnchor(String heading, Map<String, Integer> knownAnchors) {
        String normalized = heading.toLowerCase(Locale.ENGLISH).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (Strings.isEmpty(normalized)) {
            normalized = "section";
        }

        int count = knownAnchors.merge(normalized, 1, Integer::sum);
        return count == 1 ? normalized : normalized + "-" + count;
    }

    private Frontmatter extractFrontmatter(String content) {
        String normalizedContent = content.replace("\r\n", "\n");
        if (!normalizedContent.startsWith(FRONTMATTER_SEPARATOR + "\n")) {
            throw new IllegalArgumentException("Markdown KBAs require YAML frontmatter.");
        }

        int closingIndex =
                normalizedContent.indexOf("\n" + FRONTMATTER_SEPARATOR + "\n", FRONTMATTER_SEPARATOR.length());
        if (closingIndex < 0) {
            throw new IllegalArgumentException("Markdown KBA frontmatter is not properly terminated.");
        }

        String metadataBlock = normalizedContent.substring(FRONTMATTER_SEPARATOR.length() + 1, closingIndex);
        String body = normalizedContent.substring(closingIndex + FRONTMATTER_SEPARATOR.length() + 2);
        Object loadedMetadata = yaml.load(metadataBlock);
        if (!(loadedMetadata instanceof Map<?, ?> rawMap)) {
            throw new IllegalArgumentException("Markdown KBA frontmatter must contain a YAML object.");
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> metadata.put(String.valueOf(key), value));
        return new Frontmatter(metadata, body);
    }

    private String readResource(Resource resource) {
        try (InputStreamReader reader = new InputStreamReader(resource.openStream(), StandardCharsets.UTF_8)) {
            return Streams.readToString(reader);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Failed to read Markdown KBA: " + resource.getPath(), exception);
        }
    }

    private String requireString(Map<String, Object> metadata, String key) {
        String value = Value.of(metadata.get(key)).asString("").trim();
        if (Strings.isEmpty(value)) {
            throw new IllegalArgumentException("Markdown KBA frontmatter is missing '" + key + "'.");
        }
        return value;
    }

    private List<String> resolveStringList(Object raw) {
        if (raw instanceof List<?> list) {
            return list.stream()
                       .map(item -> String.valueOf(item).trim().toUpperCase(Locale.ENGLISH))
                       .filter(Strings::isFilled)
                       .toList();
        }

        String rawValue = Value.of(raw).asString("");
        if (Strings.isEmpty(rawValue)) {
            return List.of();
        }

        return Arrays.stream(rawValue.split(","))
                     .map(String::trim)
                     .map(s -> s.toUpperCase(Locale.ENGLISH))
                     .filter(Strings::isFilled)
                     .toList();
    }

    record Frontmatter(Map<String, Object> metadata, String body) {
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
