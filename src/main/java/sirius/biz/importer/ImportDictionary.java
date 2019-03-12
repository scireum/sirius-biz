/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer;

import sirius.kernel.commons.Strings;
import sirius.kernel.nls.NLS;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Contains a dictionary which maps fieldnames (names, labels, translations) to a set of effective fields.
 * <p>
 * This can be used to import data with varying field names. Aliases can be defined in the system config.
 *
 * @see BaseImportHandler#loadAliases(ImportDictionary)
 */
public class ImportDictionary {

    private Map<String, String> aliases = new HashMap<>();
    private Function<String, String> translator;

    /**
     * Creates a new dictionary with the given function used to translate a field into a label to be shown to the user.
     *
     * @param translator a function which determines the label for a given field
     */
    public ImportDictionary(Function<String, String> translator) {
        this.translator = translator;
    }

    /**
     * Returns the label for the given effective field.
     *
     * @param field the field to translate
     * @return the label to be shown to the user for the given field
     */
    public String getLabel(String field) {
        return translator.apply(field);
    }

    /**
     * Adds an alias for the given field.
     *
     * @param field the target field
     * @param alias the alias - note that it will {@link #normalize(String) normalied}
     * @return the dictionary itself for fluent method calls
     */
    public ImportDictionary withAlias(String field, String alias) {
        aliases.put(normalize(alias), field);
        return this;
    }

    /**
     * Resolves the given field (name) into an effective field by applying the known aliases.
     *
     * @param field the fieldname to search by
     * @return the effective field or an empty optional if no matching alias is present
     */
    public Optional<String> resolve(String field) {
        if (Strings.isEmpty(field)) {
            return Optional.empty();
        }
        return Optional.ofNullable(aliases.get(normalize(field)));
    }

    /**
     * Normalizes an alias or fieldname to simplify compaing them.
     * <p>
     * This will remove everything except characters, digits and "_". All characters will be lowercased.
     *
     * @param field the field to normalize
     * @return the normalized version of the field
     */
    private String normalize(String field) {
        return NLS.smartGet(field)
                  .toLowerCase()
                  .replace("ä", "ae")
                  .replace("ö", "oe")
                  .replace("ü", "ue")
                  .replace("ß", "ss")
                  .replaceAll("[^a-z0-9_]", "");
    }

    private String aliasesFor(String field) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> alias : aliases.entrySet()) {
            if (Strings.areEqual(alias.getValue(), field)) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(alias.getKey());
            }
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (String field : aliases.values()) {
            sb.append(field);
            sb.append(": ");
            sb.append(aliasesFor(field));
            sb.append("\n");
        }

        return sb.toString();
    }

    private Map<String, String> getAliases() {
        return Collections.unmodifiableMap(aliases);
    }

    /**
     * Adds an other Dictionary to this Dictionary.
     *
     * @param otherDictionary the {@link ImportDictionary} which alias entries will be added to this dictionary
     * @return the dictionary itself for fluent method calls
     */
    public ImportDictionary mergeDictionary(ImportDictionary otherDictionary) {
        otherDictionary.getAliases().forEach((alias, field) -> withAlias(field, alias));
        return this;
    }
}
