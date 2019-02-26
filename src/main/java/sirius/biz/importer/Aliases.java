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

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Executes given mappings to extract information from a given record into a {@link Context}.
 *
 * @param <R> the type of records being processed
 * @param <L> the type of index or lookups (e.g. an index as number or a string as key)
 */
public abstract class Aliases<R, L> {

    protected List<Tuple<String, L>> mappings = new ArrayList<>();

    /**
     * Extracts the given lookup from the given record.
     *
     * @param record the record to read data from
     * @param lookup the lookup key describing what to extract
     * @return the extracted value
     */
    @Nonnull
    protected abstract Value read(@Nonnull R record, @Nonnull L lookup);

    /**
     * Transforms the given lookup into its string representation.
     *
     * @param lookup the key or lookup to transform
     * @return the string representation of the given key or lookup
     */
    protected abstract String asString(@Nonnull L lookup);

    /**
     * Provides an i18n'ed translation of the given field.
     *
     * @param field the field to translate
     * @return the i18n'ed name of the field or the field itself if no translation is present
     */
    @Nonnull
    protected abstract String translateField(@Nonnull String field);

    /**
     * Transforms the given record into a context.
     *
     * @param record the record to process
     * @return the extracted mappings as context
     */
    public Context transform(R record) {
        Context result = Context.create();
        for (Tuple<String, L> mapping : mappings) {
            result.put(mapping.getFirst(), read(record, mapping.getSecond()));
        }

        return result;
    }

    /**
     * Enumerates the known mappings of this alias handler.
     *
     * @return the list of known mappings
     */
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
