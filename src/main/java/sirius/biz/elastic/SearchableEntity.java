/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.elastic;

import sirius.biz.protocol.NoJournal;
import sirius.db.es.ElasticEntity;
import sirius.db.es.annotations.Analyzed;
import sirius.db.es.annotations.ESOption;
import sirius.db.es.annotations.IndexMode;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.BeforeSave;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.types.NestedList;
import sirius.db.mixing.types.StringList;
import sirius.db.mixing.types.StringListMap;
import sirius.db.mixing.types.StringMap;
import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Strings;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Maintains a <tt>searchField</tt> in which all fields annotated with {@link SearchContent} are indexed.
 * <p>
 * While indexing, we also performe some sanity checks like limiting the min and max width of generated tokens.
 * <p>
 * The tokenizer uses {@link SearchableEntity#SPLIT_TOKEN_LEVEL_1} and {@link SearchableEntity#SPLIT_TOKEN_LEVEL_2} to
 * separate tokens and provides them in a whitespace separated format for digestion by Elasticsearch.
 */
public abstract class SearchableEntity extends ElasticEntity {

    /**
     * We will not index anything longer than 255 characters as it is pointless and might kill
     * ES (which has a max token length...)
     */
    private static final int MAX_TOKEN_LENGTH = 255;

    /**
     * We also do not index short tokens, as they are dismissed by ES anyway
     */
    private static final int MIN_TOKEN_LENGTH = 2;

    /**
     * Represents a regular expression which detects all character which aren't allowed in a search prefix.
     */
    public static final Pattern SPLIT_TOKEN_LEVEL_1 = Pattern.compile("[^\\p{L}\\d_\\-.]");

    /**
     * Represents a regular expression which detects all characters which are allowed in a search prefix but still cause
     * a token to be splitted.
     */
    public static final Pattern SPLIT_TOKEN_LEVEL_2 = Pattern.compile("[^\\p{L}]");

    /**
     * Contains manually maintained content to be added to the search field.
     */
    public static final Mapping SEARCHABLE_CONTENT = Mapping.named("searchableContent");
    @NullAllowed
    @SearchContent
    @NoJournal
    @IndexMode(indexed = ESOption.FALSE, docValues = ESOption.FALSE)
    private String searchableContent;

    /**
     * Contains the actual search data. This field should be used in search queries but must not
     * be modified manually.
     */
    public static final Mapping SEARCH_FIELD = Mapping.named("searchField");
    @Analyzed(analyzer = Analyzed.ANALYZER_WHITESPACE, indexOptions = Analyzed.IndexOption.DOCS)
    @IndexMode(indexed = ESOption.TRUE, normsEnabled = ESOption.TRUE, stored = ESOption.FALSE, excludeFromSource = true)
    @NullAllowed
    @NoJournal
    private String searchField;

    @BeforeSave
    protected void updateSearchField() {
        if (!isNew() && !isAnyMappingChanged()) {
            return;
        }

        StringBuilder contentBuilder = new StringBuilder();
        this.getDescriptor()
            .getProperties()
            .stream()
            .filter(p -> p.getAnnotation(SearchContent.class).isPresent())
            .map(p -> p.getValue(this))
            .filter(Strings::isFilled)
            .forEach(obj -> addContent(contentBuilder, obj));
        searchField = contentBuilder.toString();
    }

    /**
     * Transforms a property value into searchable text.
     *
     * @param contentBuilder the output to write to
     * @param propertyValue  the value to transform
     */
    protected void addContent(StringBuilder contentBuilder, Object propertyValue) {
        if (propertyValue instanceof StringList) {
            ((StringList) propertyValue).forEach(value -> addContentAsTokens(contentBuilder, value));
        } else if (propertyValue instanceof StringMap) {
            ((StringMap) propertyValue).forEach(entry -> {
                addContentAsTokens(contentBuilder, entry.getKey());
                addContentAsTokens(contentBuilder, entry.getValue());
            });
        } else if (propertyValue instanceof StringListMap) {
            ((StringListMap) propertyValue).forEach(entry -> {
                addContentAsTokens(contentBuilder, entry.getKey());
                entry.getValue().forEach(value -> addContentAsTokens(contentBuilder, value));
            });
        } else if (propertyValue instanceof NestedList) {
            throw new UnsupportedOperationException("Nested objects are not yet supported by SearchableEntity");
        } else {
            addContentAsTokens(contentBuilder, propertyValue);
        }
    }

    /**
     * Tokenizes the given string into the given output builder.
     *
     * @param output the builder to write to
     * @param value  the value to tokenize
     */
    @SuppressWarnings("squid:S2259")
    @Explain("input cannot be null due to Strings.isEmpty")
    public static void addContentAsTokens(StringBuilder output, Object value) {
        String input = value == null ? null : String.valueOf(value);
        if (Strings.isEmpty(input)) {
            return;
        }

        splitContent(input.toLowerCase()).forEach(token -> SearchableEntity.appendSingleToken(output, token));
    }

    private static Set<String> splitContent(String input) {
        Set<String> result = new HashSet<>();
        result.add(input);

        for (String subToken : SPLIT_TOKEN_LEVEL_1.matcher(input).replaceAll(" ").split(" ")) {
            result.add(subToken);

            Collections.addAll(result, SPLIT_TOKEN_LEVEL_2.matcher(subToken.toLowerCase()).replaceAll(" ").split(" "));
        }

        result.remove("");
        return result;
    }

    private static void appendSingleToken(StringBuilder output, String subToken) {
        int length = subToken.length();
        if (length >= MIN_TOKEN_LENGTH && length <= MAX_TOKEN_LENGTH) {
            output.append(" ");
            output.append(subToken);
        }
    }

    public String getSearchableContent() {
        return searchableContent;
    }

    public void setSearchableContent(String searchableContent) {
        this.searchableContent = searchableContent;
    }

    public String getSearchField() {
        return searchField;
    }
}
