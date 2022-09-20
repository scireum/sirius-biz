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
import sirius.db.text.BasicIndexTokenizer;
import sirius.db.text.Tokenizer;
import sirius.kernel.commons.Strings;

/**
 * Maintains a <tt>searchField</tt> in which all fields annotated with {@link SearchContent} are indexed.
 * <p>
 * Uses a {@link BasicIndexTokenizer} to tokenize the searchable content.
 */
public abstract class SearchableEntity extends ElasticEntity {

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
    @IndexMode(indexed = ESOption.TRUE, normsEnabled = ESOption.TRUE, stored = ESOption.FALSE)
    @NullAllowed
    @NoJournal
    private String searchField;

    @BeforeSave
    protected void updateSearchField() {
        if (!isNew() && !isAnyMappingChanged()) {
            return;
        }

        BasicIndexTokenizer tokenizer = new BasicIndexTokenizer();
        StringBuilder contentBuilder = new StringBuilder();
        this.getDescriptor()
            .getProperties()
            .stream()
            .filter(p -> p.getAnnotation(SearchContent.class).isPresent())
            .map(p -> p.getValue(this))
            .filter(Strings::isFilled)
            .forEach(obj -> addContent(tokenizer, contentBuilder, obj));
        searchField = contentBuilder.toString();
    }

    /**
     * Transforms a property value into searchable text.
     *
     * @param contentBuilder the output to write to
     * @param propertyValue  the value to transform
     */
    protected void addContent(Tokenizer tokenizer, StringBuilder contentBuilder, Object propertyValue) {
        if (propertyValue instanceof StringList list) {
            list.forEach(value -> addContentAsTokens(tokenizer, contentBuilder, value));
        } else if (propertyValue instanceof StringMap map) {
            map.forEach(entry -> {
                addContentAsTokens(tokenizer, contentBuilder, entry.getKey());
                addContentAsTokens(tokenizer, contentBuilder, entry.getValue());
            });
        } else if (propertyValue instanceof StringListMap map) {
           map.forEach(entry -> {
                addContentAsTokens(tokenizer, contentBuilder, entry.getKey());
                entry.getValue().forEach(value -> addContentAsTokens(tokenizer, contentBuilder, value));
            });
        } else if (propertyValue instanceof NestedList) {
            throw new UnsupportedOperationException("Nested objects are not yet supported by SearchableEntity");
        } else if (propertyValue != null) {
            addContentAsTokens(tokenizer, contentBuilder, propertyValue.toString());
        }
    }

    private void addContentAsTokens(Tokenizer tokenizer, StringBuilder contentBuilder, String value) {
        tokenizer.acceptPlain(value, token -> contentBuilder.append(" ").append(token));
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
