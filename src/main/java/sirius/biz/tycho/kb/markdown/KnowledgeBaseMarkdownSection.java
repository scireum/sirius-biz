/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.kb.markdown;

/**
 * Represents one rendered section of a Markdown knowledge base article.
 *
 * @param heading the section heading (empty for the intro section)
 * @param anchor  the anchor ID for deep linking (empty if no heading)
 * @param html    the rendered HTML body of the section
 */
public record KnowledgeBaseMarkdownSection(String heading, String anchor, String html) {
}
