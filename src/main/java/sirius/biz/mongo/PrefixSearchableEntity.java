/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.mongo;

import sirius.biz.protocol.NoJournal;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.BeforeSave;
import sirius.db.mixing.annotations.Transient;
import sirius.db.mixing.types.StringList;
import sirius.db.mongo.MongoEntity;
import sirius.db.text.Tokenizer;
import sirius.kernel.commons.Strings;

import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Maintains a <tt>prefixSearchField</tt> in which all fields annotated with {@link PrefixSearchContent} are indexed.
 * <p>
 * Note that the <tt>searchPrefixes</tt> is not automatically indexed as it usually only makes sense if indexed
 * in combination with other field(s). Therefore make sure to include this field when extending this class into
 * other {@link MongoEntity entities} where applicable. {@link sirius.biz.tenants.mongo.MongoTenantAware} is a good
 * example.
 */
public abstract class PrefixSearchableEntity extends MongoEntity {

    /**
     * Represents a regular expression which detects all characters (except whitespace)
     * which aren't allowed in a prefix filter.
     *
     * @see sirius.db.mongo.constraints.MongoFilterFactory#prefix(Mapping, String)
     */
    public static final Pattern NON_PREFIX_OR_WHITESPACE_CHARACTER = Pattern.compile("[^0-9\\p{L}_\\-@.#\\s]");

    /**
     * Contains manually maintained content to be added to the search field.
     */
    public static final Mapping SEARCH_PREFIXES = Mapping.named("searchPrefixes");
    @NoJournal
    private final StringList searchPrefixes = new StringList();

    @Transient
    private boolean forceUpdateSearchPrefixes;

    @BeforeSave(priority = 10)
    protected void updateSearchField() {
        if (!isNew() && !forceUpdateSearchPrefixes && !isAnyMappingChanged()) {
            return;
        }

        getSearchPrefixes().clear();

        Tokenizer tokenizer = createPrefixTokenizer();
        this.getDescriptor()
            .getProperties()
            .stream()
            .filter(p -> p.getAnnotation(PrefixSearchContent.class).isPresent())
            .map(p -> p.tryAs(PrefixSearchableContentConsumer.class).orElse((entity, consumer) -> {
                consumer.accept(p.getValue(this));
            }))
            .forEach(consumer -> consumer.accept(this, value -> addContentAsTokens(tokenizer, value)));

        addCustomSearchPrefixes(token -> addContentAsTokens(tokenizer, token));
    }

    /**
     * Adds custom fields as search prefixes.
     * <p>
     * This method is empty by default and intended to be overwritten by sub classes.
     *
     * @param contentConsumer can be supplied with strings to be tokenized and added to the list
     *                        of search prefixes
     */
    protected void addCustomSearchPrefixes(Consumer<String> contentConsumer) {
        // Empty by default, to be overwritten by sub classes
    }

    /**
     * Creates the tokenizer to use.
     *
     * @return a new tokenier used to fill the search prefix list
     */
    protected Tokenizer createPrefixTokenizer() {
        return new PrefixTokenizer();
    }

    /**
     * Tokenizes the given string into the given output builder.
     * <p>
     * Note, that most special characters will be removed as they're not allowed in a prefix filter.
     *
     * @param tokenizer the tokenizer to use
     * @param value     the value to tokenize
     * @see sirius.db.mongo.constraints.MongoFilterFactory#prefix(Mapping, String)
     */
    protected void addContentAsTokens(Tokenizer tokenizer, Object value) {
        if (Strings.isEmpty(value)) {
            return;
        }
        String normalizedValue = NON_PREFIX_OR_WHITESPACE_CHARACTER.matcher(value.toString()).replaceAll("");
        tokenizer.acceptPlain(normalizedValue, searchPrefixes::add);
    }

    public StringList getSearchPrefixes() {
        return searchPrefixes;
    }

    /**
     * Forces the search prefixes to be recalculated on the next {@link BeforeSave} event.
     */
    public void forceUpdateOfSearchPrefixes() {
        this.forceUpdateSearchPrefixes = true;
    }
}
