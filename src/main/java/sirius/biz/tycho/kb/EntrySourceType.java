/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.kb;

/**
 * Describes how a knowledge base article is sourced and rendered.
 */
public enum EntrySourceType {

    /**
     * A classic Tagliatelle based article.
     */
    TAGLIATELLE,

    /**
     * A Markdown article with YAML frontmatter.
     */
    MARKDOWN
}
