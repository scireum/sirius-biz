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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class ImportDictionary {

    private Map<String, String> aliases = new HashMap<>();
    private Function<String, String> translator;

    public ImportDictionary(Function<String, String> translator) {
        this.translator = translator;
    }

    public ImportDictionary withAlias(String field, String alias) {
        aliases.put(normalize(alias), field);
        return this;
    }

    public Optional<String> resolve(String field) {
        if (Strings.isEmpty(field)) {
            return Optional.empty();
        }
        return Optional.ofNullable(aliases.get(normalize(field)));
    }

    private String normalize(String field) {
        return NLS.smartGet(field)
                  .toLowerCase()
                  .replace("ä", "ae")
                  .replace("ö", "oe")
                  .replace("ü", "ue")
                  .replace("ß", "ss")
                  .replaceAll("[^a-z0-9_]", "");
    }

    public String aliasesFor(String field) {
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

    public String getLabel(String field) {
        return translator.apply(field);
    }
}
