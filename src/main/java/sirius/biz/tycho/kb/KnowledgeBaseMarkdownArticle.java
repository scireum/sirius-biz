/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.kb;

import org.commonmark.node.Node;

import java.util.List;

/**
 * Represents a Markdown knowledge base article after frontmatter parsing.
 */
public record KnowledgeBaseMarkdownArticle(String resourcePath, String articleId, String language, String title,
                                           String description, String parentId, int priority, String permissions,
                                           boolean chapter, List<String> crossReferences, Node markdown) {

    /**
     * Constructs a new {@code KnowledgeBaseMarkdownArticle} instance and ensures that the list of cross-references
     * is immutable by creating an unmodifiable copy of the provided list.
     *
     * @param resourcePath    the resource path of the source file
     * @param articleId       the unique article code (uppercase)
     * @param language        the two-letter language code
     * @param title           the article title
     * @param description     a short description (may be empty)
     * @param parentId        the parent chapter code (uppercase, may be empty)
     * @param priority        the sort priority
     * @param permissions     required permissions (may be empty)
     * @param chapter         whether this article is a chapter
     * @param crossReferences list of related article codes (uppercase)
     * @param markdown        the parsed Markdown content
     */
    public KnowledgeBaseMarkdownArticle {
        crossReferences = List.copyOf(crossReferences);
    }
}
