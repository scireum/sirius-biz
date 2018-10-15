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
import sirius.db.mixing.annotations.Index;
import sirius.db.mixing.types.NestedList;
import sirius.db.mixing.types.StringList;
import sirius.db.mixing.types.StringListMap;
import sirius.db.mixing.types.StringMap;
import sirius.db.mongo.Mango;
import sirius.db.mongo.MongoEntity;
import sirius.db.mongo.constraints.MongoFilterFactory;
import sirius.kernel.commons.Strings;

/**
 * Maintains a <tt>prefixSearchField</tt> in which all fields annotated with {@link PrefixSearchContent} are indexed.
 */
@Index(name = "prefix_index", columns = "searchPrefixes", columnSettings = Mango.INDEX_ASCENDING)
public abstract class PrefixSearchableEntity extends MongoEntity {

    /**
     * We will not index anything longer than 255 characters as it is pointless.
     */
    private static final int MAX_TOKEN_LENGTH = 255;

    /**
     * Contains manually maintained content to be added to the search field.
     */
    public static final Mapping SEARCH_PREFIXES = Mapping.named("searchPrefixes");
    @NoJournal
    private final StringList searchPrefixes = new StringList();

    @BeforeSave
    protected void updateSearchField() {
        if (!isNew()
            && getDescriptor().isFetched(this, getDescriptor().getProperty(SEARCH_PREFIXES))
            && !isAnyMappingChanged()) {
            return;
        }

        getSearchPrefixes().clear();

        this.getDescriptor()
            .getProperties()
            .stream()
            .filter(p -> p.getAnnotation(PrefixSearchContent.class).isPresent())
            .map(p -> p.getValue(this))
            .filter(Strings::isFilled)
            .forEach(this::addContent);
    }

    /**
     * Transforms a property value into searchable text.
     *
     * @param propertyValue the value to transform
     */
    protected void addContent(Object propertyValue) {
        if (propertyValue instanceof StringList) {
            ((StringList) propertyValue).forEach(this::addContentAsTokens);
        } else if (propertyValue instanceof StringMap) {
            ((StringMap) propertyValue).forEach(entry -> {
                addContentAsTokens(entry.getKey());
                addContentAsTokens(entry.getValue());
            });
        } else if (propertyValue instanceof StringListMap) {
            ((StringListMap) propertyValue).forEach(entry -> {
                addContentAsTokens(entry.getKey());
                entry.getValue().forEach(this::addContentAsTokens);
            });
        } else if (propertyValue instanceof NestedList) {
            throw new UnsupportedOperationException("Nested objects are not yet supported by PrefixSearchableEntity");
        } else {
            addContentAsTokens(propertyValue);
        }
    }

    /**
     * Tokenizes the given string into the given output builder.
     *
     * @param value the value to tokenize
     */
    public void addContentAsTokens(Object value) {
        String input = value == null ? null : String.valueOf(value);
        if (Strings.isEmpty(input)) {
            return;
        }

        String tokenInLowerCase = input.toLowerCase();
        for (String subToken : MongoFilterFactory.NON_PREFIX_CHARACTER.matcher(tokenInLowerCase)
                                                                      .replaceAll(" ")
                                                                      .split(" ")) {
            appendSingleToken(subToken);
        }
    }

    private void appendSingleToken(String subToken) {
        int length = subToken.length();
        if (length <= MAX_TOKEN_LENGTH) {
            getSearchPrefixes().modify().add(subToken.toLowerCase());
        }
    }

    public StringList getSearchPrefixes() {
        return searchPrefixes;
    }
}
