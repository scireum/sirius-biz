/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.mongo;

import sirius.db.text.ChainableTokenProcessor;
import sirius.db.text.DeduplicateProcessor;
import sirius.db.text.PatternReplaceProcessor;
import sirius.db.text.PatternSplitProcessor;
import sirius.db.text.PipelineProcessor;
import sirius.db.text.ToLowercaseProcessor;
import sirius.db.text.TokenLimitProcessor;
import sirius.db.text.Tokenizer;

import java.util.regex.Pattern;

/**
 * Provides the tokenizer which computes prefixes for the {@link PrefixSearchableEntity}.
 */
public class PrefixTokenizer extends Tokenizer {

    /**
     * Represents a regular expression which detects all characters which aren't allowed in a search prefix.
     */
    private static final Pattern SPLIT_TOKEN_LEVEL_1 = Pattern.compile("[^\\p{L}\\d_\\-.]");

    /**
     * Represents a regular expression which detects all characters which are allowed in a search prefix but still cause
     * a token to be splitted.
     */
    private static final Pattern SPLIT_TOKEN_LEVEL_2 = Pattern.compile("[^\\p{L}]");

    @Override
    protected ChainableTokenProcessor createProcessor() {
        return new PipelineProcessor(PatternReplaceProcessor.createRemoveControlCharacters(),
                                     new PatternSplitProcessor(SPLIT_TOKEN_LEVEL_1, true, true),
                                     new PatternSplitProcessor(SPLIT_TOKEN_LEVEL_2, true, true),
                                     new TokenLimitProcessor(1, 255),
                                     new ToLowercaseProcessor(),
                                     new DeduplicateProcessor(true));
    }
}
