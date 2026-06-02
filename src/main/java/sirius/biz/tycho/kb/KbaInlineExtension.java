/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.kb;

import org.commonmark.Extension;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.commonmark.parser.PostProcessor;
import org.commonmark.parser.beta.InlineContentParser;
import org.commonmark.parser.beta.InlineContentParserFactory;
import org.commonmark.parser.beta.InlineParserState;
import org.commonmark.parser.beta.ParsedInline;
import org.commonmark.parser.beta.Scanner;
import sirius.kernel.commons.Strings;

import java.util.Set;

/**
 * Recognizes knowledge-base article references in Markdown.
 * <p>
 * Parsed references are represented as {@link KbaReferenceNode} instances and rendered by
 * {@link KbaReferenceNodeRenderer}.
 * <p>
 * Two inline syntaxes are supported:
 * <ul>
 *     <li><b>Angle-bracket syntax</b> — {@code <kba:ARTICLE>} or {@code <kba:ARTICLE#anchor>}
 *     uses the resolved article title as link text.</li>
 *     <li><b>Markdown link syntax</b> — {@code [Custom label](kba:ARTICLE)} or
 *     {@code [Custom label](kba:ARTICLE#anchor)} uses the bracket text as link label.</li>
 * </ul>
 * <p>
 * In both forms, {@code ARTICLE} is the article code (letters and digits only). An optional
 * {@code #anchor} targets a section within the article; anchor names may contain letters, digits,
 * hyphens, and underscores. Link destinations must match {@code kba:ARTICLE} or
 * {@code kba:ARTICLE#anchor} exactly (see {@link KbaReferenceNode#fromDestination(String, String)}).
 */
public class KbaInlineExtension implements Parser.ParserExtension {

    /**
     * Creates a new {@link KbaInlineExtension} for use with CommonMark.
     *
     * @return the extension instance
     */
    public static Extension create() {
        return new KbaInlineExtension();
    }

    @Override
    public void extend(Parser.Builder parserBuilder) {
        parserBuilder.customInlineContentParserFactory(new KbaInlineParserExtension())
                     .postProcessor(new KbaMarkdownLinkPostProcessor());
    }

    private static final class KbaInlineParserExtension implements InlineContentParserFactory {

        @Override
        public Set<Character> getTriggerCharacters() {
            return Set.of('<');
        }

        @Override
        public InlineContentParser create() {
            return new KbaInlineContentParser();
        }
    }

    private static final class KbaInlineContentParser implements InlineContentParser {

        @Override
        public ParsedInline tryParse(InlineParserState inlineParserState) {
            Scanner scanner = inlineParserState.scanner();
            var start = scanner.position();
            if (!scanner.next('<') || !scanner.next("kba:")) {
                scanner.setPosition(start);
                return ParsedInline.none();
            }

            String articleCode = parsePart(scanner, Character::isLetterOrDigit);
            if (Strings.isEmpty(articleCode)) {
                scanner.setPosition(start);
                return ParsedInline.none();
            }

            String anchor = "";
            if (scanner.next('#')) {
                anchor = parsePart(scanner,
                                   character -> Character.isLetterOrDigit(character)
                                                || character == '-'
                                                || character == '_');
                if (Strings.isEmpty(anchor)) {
                    scanner.setPosition(start);
                    return ParsedInline.none();
                }
            }

            if (!scanner.next('>')) {
                scanner.setPosition(start);
                return ParsedInline.none();
            }

            return ParsedInline.of(new KbaReferenceNode(articleCode, anchor, null), scanner.position());
        }

        private String parsePart(Scanner scanner, CharacterPredicate predicate) {
            StringBuilder result = new StringBuilder();
            while (scanner.hasNext() && predicate.matches(scanner.peek())) {
                result.append(scanner.peek());
                scanner.next();
            }

            return result.toString();
        }
    }

    private static final class KbaMarkdownLinkPostProcessor implements PostProcessor {

        @Override
        public Node process(Node node) {
            transformLinks(node);
            return node;
        }

        private void transformLinks(Node node) {
            Node current = node.getFirstChild();
            while (current != null) {
                Node next = current.getNext();
                transformLinks(current);
                if (current instanceof Link link) {
                    KbaReferenceNode.fromDestination(link.getDestination(), extractLabel(link))
                                    .ifPresent(referenceNode -> {
                                        referenceNode.setSourceSpans(link.getSourceSpans());
                                        link.insertBefore(referenceNode);
                                        link.unlink();
                                    });
                }
                current = next;
            }
        }

        private String extractLabel(Link link) {
            StringBuilder label = new StringBuilder();
            appendText(link, label);
            return label.toString().strip();
        }

        private void appendText(Node node, StringBuilder label) {
            if (node instanceof Text text) {
                label.append(text.getLiteral());
            } else if (node instanceof SoftLineBreak || node instanceof HardLineBreak) {
                label.append(' ');
            }

            for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
                appendText(child, label);
            }
        }
    }

    @FunctionalInterface
    private interface CharacterPredicate {

        boolean matches(char character);
    }
}
