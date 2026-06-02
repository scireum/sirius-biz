/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.kb.markdown;

import org.commonmark.node.CustomNode;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a knowledge-base reference found in Markdown content.
 */
public class ArticleReferenceNode extends CustomNode {

    /**
     * Used to parse {@code kba:} link destinations (article code and optional anchor).
     */
    private static final Pattern KBA_DESTINATION_PATTERN =
            Pattern.compile("^kba:([a-zA-Z0-9]+)(?:#([a-zA-Z0-9-_]+))?$");

    /**
     * Contains the referenced article code.
     */
    private final String articleCode;

    /**
     * Contains the optional section anchor (empty if none was specified).
     */
    private final String anchor;

    /**
     * Contains the optional link label from the Markdown text (empty if none was specified).
     */
    private final String customLabel;

    /**
     * Creates a new knowledge-base reference node.
     *
     * @param articleCode the referenced article code
     * @param anchor      an optional section anchor (may be empty)
     * @param customLabel an optional link label (may be empty)
     */
    public ArticleReferenceNode(String articleCode, @Nullable String anchor, @Nullable String customLabel) {
        this.articleCode = articleCode;
        this.anchor = Value.of(anchor).trim();
        this.customLabel = Value.of(customLabel).trim();
    }

    /**
     * Parses a Markdown link destination into a knowledge-base reference, if applicable.
     *
     * @param destination the link destination (e.g. {@code kba:ARTICLE#anchor})
     * @param customLabel an optional link label taken from the Markdown text
     * @return the parsed reference, or empty if the destination is not a KB link
     */
    public static Optional<ArticleReferenceNode> fromDestination(@Nullable String destination,
                                                                 @Nullable String customLabel) {
        if (Strings.isEmpty(destination)) {
            return Optional.empty();
        }

        Matcher matcher = KBA_DESTINATION_PATTERN.matcher(destination);
        if (!matcher.matches()) {
            return Optional.empty();
        }

        return Optional.of(new ArticleReferenceNode(matcher.group(1), matcher.group(2), customLabel));
    }

    public String getArticleCode() {
        return articleCode;
    }

    public Optional<String> getAnchor() {
        return Optional.ofNullable(anchor).filter(Strings::isFilled);
    }

    public Optional<String> getCustomLabel() {
        return Optional.ofNullable(customLabel).filter(Strings::isFilled);
    }
}
