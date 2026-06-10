/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.kb.markdown;

import sirius.kernel.commons.Strings;

import java.util.List;

/**
 * Contains the rendered contents of a Markdown knowledge base article.
 */
public record KnowledgeBaseMarkdownDocument(List<KnowledgeBaseMarkdownSection> sections) {

    /**
     * Creates a new immutable knowledge base markdown document.
     *
     * @param sections the list of rendered sections to include in the document, which will be
     *                 copied to ensure immutability
     */
    public KnowledgeBaseMarkdownDocument {
        sections = List.copyOf(sections);
    }

    /**
     * Determines whether this document has enough headings to warrant a table of contents.
     *
     * @return <tt>true</tt> if the document contains more than one headed section, <tt>false</tt> otherwise
     */
    public boolean hasTableOfContents() {
        return tableOfContentsSections().size() > 1;
    }

    /**
     * Returns the sections which should appear in the generated table of contents.
     *
     * @return all sections which provide both a heading and anchor
     */
    public List<KnowledgeBaseMarkdownSection> tableOfContentsSections() {
        return sections.stream()
                       .filter(section -> Strings.isFilled(section.heading()))
                       .filter(section -> Strings.isFilled(section.anchor()))
                       .toList();
    }
}
