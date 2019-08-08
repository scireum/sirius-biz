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
 */
public class ValueInListCheck implements ValueCheck {

    private Set<String> values;

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
        if (!values.contains(value.asString())) {
            throw new IllegalArgumentException(NLS.fmtr("ValueInListCheck.errorMsg")
                                                  .set("value", value.asString())
                                                  .format());
        }
    }

    @Override
    public String generateRemark() {
        return NLS.fmtr("ValueInListCheck.remark").set("values", Strings.join(values, ", ")).format();
    }
}
