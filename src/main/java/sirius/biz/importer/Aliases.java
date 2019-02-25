/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer;

import sirius.kernel.commons.Context;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Value;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public abstract class Aliases<R, L> {

    protected List<Tuple<String, L>> mappings = new ArrayList<>();

    protected abstract Value read(R record, L lookup);

    protected abstract String asString(L lookup);

    protected abstract String translateField(String field);

    public Context transform(R record) {
        Context result = Context.create();
        for (Tuple<String, L> mapping : mappings) {
            result.put(mapping.getFirst(), read(record, mapping.getSecond()));
        }

        return result;
    }

    public List<Tuple<String, L>> getMappings() {
        return Collections.unmodifiableList(mappings);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Mappings\n");
        sb.append("-----------------------------------------\n");
        for (Tuple<String, L> mapping : mappings) {
            sb.append(asString(mapping.getSecond()));
            sb.append(": ");
            sb.append(translateField(mapping.getFirst()));
            sb.append("\n");
        }

        return sb.toString();
    }

}
