/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer;

import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Value;
import sirius.kernel.commons.Values;
import sirius.kernel.nls.NLS;

import java.util.Optional;

public class LineBasedAliases extends Aliases<Values, Integer> {

    private final ImportDictionary dictionary;

    public LineBasedAliases(Values row, ImportDictionary dictionary) {
        this.dictionary = dictionary;

        for (int i = 0; i < row.length(); i++) {
            Optional<String> field = dictionary.resolve(row.at(i).asString());
            if (field.isPresent()) {
                mappings.add(Tuple.create(field.get(), i));
            }
        }
    }

    @Override
    protected String translateField(String field) {
        return dictionary.getLabel(field);
    }

    @Override
    protected Value read(Values record, Integer lookup) {
        return record.at(lookup);
    }

    @Override
    protected String asString(Integer lookup) {
        return NLS.fmtr("LineBasedAliases.column").set("column", lookup + 1).format();
    }
}
