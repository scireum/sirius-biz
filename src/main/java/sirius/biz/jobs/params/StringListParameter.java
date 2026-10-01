/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.params;

import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.nls.NLS;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Provides a multi select parameter which accepts arbitrary strings entered by the user.
 * <p>
 * In contrast to {@link MultiSelectStringParameter} there is no fixed list of selectable values. The values are
 * encoded the same way, so that both parameters can be unified later without migrating stored values.
 */
public class StringListParameter extends MultiSelectParameter<String, StringListParameter> {

    /**
     * Creates a new parameter with the given name and label.
     *
     * @param name  the name of the parameter
     * @param label the label of the parameter, which will be {@link NLS#smartGet(String) auto translated}
     */
    public StringListParameter(String name, String label) {
        super(name, label);
    }

    @Override
    public List<MultiSelectValue> getValues(Map<String, String> context) {
        return get(context).orElseGet(Collections::emptyList)
                           .stream()
                           .map(value -> new MultiSelectValue(value, value, true))
                           .toList();
    }

    @Override
    public String getTemplateName() {
        return "/templates/biz/jobs/params/selectStringList.html.pasta";
    }

    @Override
    protected String createValueName(String value) {
        return value;
    }

    @Override
    protected String createValueLabel(String value) {
        return value;
    }

    @Nullable
    @Override
    protected String checkAndTransformSingleValue(Value input) {
        String rawInput = input.asString().trim();

        // we can not allow the delimiter within values, as we obviously use it to separate values from each other
        if (Strings.isEmpty(rawInput) || rawInput.contains(DELIMITER)) {
            return null;
        }

        return rawInput;
    }

    @Override
    protected Optional<String> resolveSingleValueFromString(Value input) {
        return Optional.ofNullable(checkAndTransformSingleValue(input));
    }
}
