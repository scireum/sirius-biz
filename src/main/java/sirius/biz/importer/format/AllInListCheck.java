/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer.format;

import sirius.kernel.commons.Value;
import sirius.kernel.nls.NLS;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Ensures that all values in the associated field are contained in the given list.
 * <p>
 * Note that empty values are ignored by this check. Use a {@link RequiredCheck} or
 * {@link FieldDefinition#markRequired()} to strongly enforce the value list.
 * <p>
 * Note, as most of the {@link Value} methods will perform an automatic <tt>trim</tt>, we also trim the contents before
 * checking if the value is in the list. Use {@link #checkUntrimmed()} to suppress this behaviour.
 */
public class AllInListCheck extends ValueInListCheck {

    protected final String separator;

    /**
     * Creates a new check for the given list of permitted values.
     * <p>
     * Note that all values are {@link NLS#smartGet(String) smart translated}.
     *
     * @param separator   the separator used to split the values to check
     * @param validValues the list of permitted values
     */
    public AllInListCheck(String separator, Collection<String> validValues) {
        super(validValues);
        this.separator = separator;
    }

    @Override
    public void perform(Value value) {
        if (value.isEmptyString()) {
            return;
        }

        Stream<String> items = Arrays.stream(value.getRawString().split(separator));
        if (trim) {
            items = items.map(String::trim);
        }
        List<String> invalidItems = items.filter(item -> !values.contains(item)).toList();

        if (invalidItems.isEmpty()) {
            return;
        }

        if (invalidItems.size() == 1) {
            throw new IllegalArgumentException(NLS.fmtr("ValueInListCheck.errorMsg")
                                                  .setDirect("value", invalidItems.getFirst())
                                                  .format());
        } else {
            throw new IllegalArgumentException(NLS.fmtr("AllInListCheck.errorMsg")
                                                  .setDirect("values",
                                                             invalidItems.stream()
                                                                         .map(item -> "'" + item + "'")
                                                                         .collect(Collectors.joining(", ")))
                                                  .format());
        }
    }
}
