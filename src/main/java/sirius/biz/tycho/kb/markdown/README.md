# Markdown Knowledge Base Support

This package contains the Markdown rendering pipeline for knowledge base articles. It translates `.md` resources into
the same Tycho knowledge base page model used by classic Tagliatelle articles.

## Pipeline

1. `SynchronizeArticlesTask` discovers `.md` files below `kb/` resources and asks `KnowledgeBaseMarkdownRenderer` to
   load their metadata.
2. `KnowledgeBaseMarkdownRenderer.loadArticle(...)` reads the resource, normalizes line endings, parses the Markdown
   with CommonMark, and extracts YAML frontmatter.
3. Frontmatter is mapped to `KnowledgeBaseMarkdownArticle`, which carries the KB metadata plus the parsed CommonMark
   document tree.
4. `KnowledgeBaseMarkdownRenderer.renderDocument(...)` walks the top-level CommonMark nodes and splits the article into
   `KnowledgeBaseMarkdownSection` entries. Headings up to level 2 start new sections.
5. Each section body is rendered to HTML by the configured CommonMark `HtmlRenderer`.
6. The page template renders `KnowledgeBaseMarkdownDocument` inside `<k:base>`, adds the table of contents from headed
   sections, and injects each section body as raw HTML.

## Frontmatter

Markdown articles require YAML frontmatter with the same metadata as classic KB articles:

- `code`: article code, stored uppercase.
- `lang`: article language.
- `title`: article title.
- `description`: optional short description.
- `parent`: parent chapter/article code, stored uppercase.
- `priority`: optional sorting priority, defaults to `100`.
- `permissions`: optional permission expression.
- `chapter`: optional boolean, defaults to `false`.
- `crossReferences`: optional comma-separated or YAML-list article codes, stored uppercase.

## Supported Markdown

The renderer uses CommonMark with these extensions:

- GitHub-style tables via `TablesExtension`.
- GitHub-style alerts via `AlertsExtension`.
- YAML frontmatter via `YamlFrontMatterExtension`.
- Custom KB references via `KbaInlineExtension`.

Normal CommonMark constructs such as paragraphs, headings, lists, links, emphasis, inline code, block quotes, and fenced
code blocks are supported.

## Rendering Extensions

### Sections and Anchors

Top-level headings of level 1 and 2 become KB sections. The section heading is rendered with `TextContentRenderer` and
converted to a lowercase, URL-safe anchor. Duplicate anchors receive a numeric suffix such as `section-2`.

Content before the first section heading is kept as an intro section without an anchor.

### Code Blocks

Fenced code blocks are rendered through `/taglib/k/code.html.pasta` to use Tycho's `prettyprint` classes. Language
identifiers become `lang-<language>` classes.

Fenced code blocks with the language `mermaid` are rendered through `/taglib/k/mermaid.html.pasta` as:

```html
<div class="mermaid kb-diagram">...</div>
```

### Tables

Markdown tables are rendered as Tycho-styled tables using:

```html
class="table table-striped table-small-text"
```

### Alerts

`TychoAlertNodeRenderer` maps GitHub-style alerts to Tycho card markup:

- `[!NOTE]` uses the blue info style.
- `[!TIP]` uses the green lightbulb style.
- `[!WARNING]` uses the yellow warning style.

Alert headings are resolved through NLS keys named `TychoAlertNodeRenderer.type.<TYPE>`.

### Knowledge Base References

`KbaInlineExtension` supports two KB reference forms:

- Angle bracket syntax: `<kba:ARTICLE>` or `<kba:ARTICLE#anchor>`.
- Markdown link syntax: `[Custom label](kba:ARTICLE)` or `[Custom label](kba:ARTICLE#anchor)`.

Both syntaxes produce a `KbaReferenceNode`. `KbaReferenceNodeRenderer` renders that node through
`/taglib/k/ref.html.pasta`, so resolved links, missing-article warnings, icons, tooltips, and permission behavior stay
aligned with classic Tagliatelle KB articles.

Custom Markdown labels are escaped before being passed into the tag because `k:ref` treats its label as raw HTML.

### Images

Standalone image paragraphs are handled by `KbaPreviewImageNodeRenderer` and rendered through
`/taglib/k/previewImage.html.pasta`.

The Markdown image destination becomes the image URL. The Markdown alt text becomes the preview title. The description
is currently empty.

Inline images inside other paragraphs are not converted to preview cards; they follow normal CommonMark rendering.

## Security Notes

The CommonMark renderer is configured with:

- `escapeHtml(true)` to escape raw HTML from Markdown sources.
- `sanitizeUrls(true)` to avoid unsafe URLs in standard CommonMark links and images.

Custom renderers that call Tagliatelle templates are responsible for passing escaped values when templates expect raw
HTML.

## Boundaries

This package only parses and renders Markdown article content. Article routing, permission checks, current-article
context, child article listings, breadcrumbs, and page layout remain owned by the surrounding knowledge base controller,
`KBHelper`, and Tagliatelle templates.
