/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer.format;

import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.nls.NLS;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Ensures that a value in the associated field is one of a given list.
 * <p>
 * Note that empty values are ignored by this check. Use a {@link RequiredCheck} or
 * {@link FieldDefinition#markRequired()} to strongly enforce the value list.
 * <p>
 * Note, as most of the {@link Value} methods will perform an automatic <tt>trim</tt>, we also trim the contents before
 * checking if the value is in the list. Use {@link #checkUntrimmed()} to suppress this behaviour.
 */
public class ValueInListCheck extends StringCheck {

    private final Set<String> values;

    /**
     * Creates a new check for the given list of permitted values.
     * <p>
     * Note that all values are {@link NLS#smartGet(String) smart translated}.
     *
     * @param validValues the list of permitted values
     */
    public ValueInListCheck(Collection<String> validValues) {
        this.values = validValues.stream()
                                 .filter(Objects::nonNull)
                                 .map(NLS::smartGet)
                                 .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Creates a new check for the given array of permitted values.
     * <p>
     * Note that all values are {@link NLS#smartGet(String) smart translated}.
     *
     * @param validValues the array of permitted values
     */
    public ValueInListCheck(String... validValues) {
        this(Arrays.asList(validValues));
    }

    @Override
    public void perform(Value value) {
        String effectiveValue = determineEffectiveValue(value);
        if (Strings.isEmpty(effectiveValue)) {
            return;
        }

        if (!values.contains(effectiveValue)) {
            throw new IllegalArgumentException(NLS.fmtr("ValueInListCheck.errorMsg")
                                                  .setDirect("value", effectiveValue)
                                                  .format());
        }
    }

    @Override
    public String generateRemark() {
        return NLS.fmtr("ValueInListCheck.remark").set("values", Strings.join(values, ", ")).format();
    }
}
