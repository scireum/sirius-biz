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
import sirius.db.mixing.annotations.Transient;
import sirius.db.mixing.types.NestedList;
import sirius.db.mixing.types.StringList;
import sirius.db.mixing.types.StringListMap;
import sirius.db.mixing.types.StringMap;
import sirius.db.mongo.Mango;
import sirius.db.mongo.MongoEntity;
import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Strings;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Maintains a <tt>prefixSearchField</tt> in which all fields annotated with {@link PrefixSearchContent} are indexed.
 *
 * Note that the <tt>searchPrefixes</tt> is not automatically indexed as it usually only makes sense if indexed
 * in combination with other field(s). Therefore make sure to include this field when extending this class into
 * other {@link MongoEntity entities} where applicable. {@link sirius.biz.tenants.mongo.MongoTenantAware} is a good
 * example.
 */
public abstract class PrefixSearchableEntity extends MongoEntity {

    /**
     * We will not index anything longer than 255 characters as it is pointless.
     */
    private static final int MAX_TOKEN_LENGTH = 255;

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
    @SuppressWarnings("squid:S2259")
    @Explain("input cannot be null due to Strings.isEmpty")
    public void addContentAsTokens(Object value) {
        String input = value == null ? null : String.valueOf(value);
        if (Strings.isEmpty(input)) {
            return;
        }

        splitContent(input.toLowerCase()).forEach(this::appendSingleToken);
    }

    private Set<String> splitContent(String input) {
        Set<String> result = new HashSet<>();
        result.add(input);

        for (String subToken : SPLIT_TOKEN_LEVEL_1.matcher(input).replaceAll(" ").split(" ")) {
            result.add(subToken);

            Collections.addAll(result, SPLIT_TOKEN_LEVEL_2.matcher(subToken.toLowerCase()).replaceAll(" ").split(" "));
        }

        result.remove("");
        return result;
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

    /**
     * Forces the search prefixes to be recalculated on the next {@link BeforeSave} event.
     */
    public void forceUpdateOfSearchPrefixes() {
        this.forceUpdateSearchPrefixes = true;
    }
}
