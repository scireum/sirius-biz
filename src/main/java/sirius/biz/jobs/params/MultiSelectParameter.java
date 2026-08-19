/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.params;

import sirius.kernel.commons.Json;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import tools.jackson.databind.node.ArrayNode;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Provides a base class for parameters which permit to select multiple values.
 * <p>
 * The selectable options are either rendered as a fixed list (as enumerated by {@link #getValues(Map)}, see
 * {@link MultiSelectStringParameter}) or suggested dynamically via an autocomplete URI (as provided by
 * {@link #getSuggestionUri()}, see {@link MultiSelectEntityParameter}).
 * <p>
 * The selected values are encoded in a single string, delimited by {@link #DELIMITER}. Subclasses only need to
 * provide the handling of a single value via {@link #checkAndTransformSingleValue(Value)} and
 * {@link #resolveSingleValueFromString(Value)}.
 *
 * @param <V> the type of values produced by this parameter
 * @param <P> recursive type reference to support fluent method calls
 */
public abstract class MultiSelectParameter<V, P extends MultiSelectParameter<V, P>>
        extends ParameterBuilder<List<V>, P> {

    /**
     * Defines the character used to delimit multiple values while encoded in a single string.
     */
    protected static final String DELIMITER = "|";

    /**
     * Creates a new parameter with the given name and label.
     *
     * @param name  the name of the parameter
     * @param label the label of the parameter, which will be
     *              {@link sirius.kernel.nls.NLS#smartGet(String) auto translated}
     */
    protected MultiSelectParameter(String name, String label) {
        super(name, label);
    }

    /**
     * Returns a list of all selectable values provided by the parameter in relation to the
     * given {@linkplain Map context}.
     *
     * @param context the context to read the selection state from
     * @return a list of {@link MultiSelectValue entries} which are available for this parameter in relation to the
     * given context
     */
    public abstract List<MultiSelectValue> getValues(Map<String, String> context);

    /**
     * Returns the URI used to provide suggestions for user input.
     *
     * @return the suggestion URI or an empty string if the parameter solely operates on the fixed list of
     * options rendered via {@link #getValues(Map)}
     */
    public String getSuggestionUri() {
        return "";
    }

    @Override
    public String getTemplateName() {
        return "/templates/biz/jobs/params/selectMulti.html.pasta";
    }

    /**
     * Returns the serialized name of the given value, used as the <tt>value</tt> of its option in the UI.
     *
     * @param value the value to derive the name from
     * @return the serialized name of the given value
     */
    protected abstract String createValueName(V value);

    /**
     * Returns the label to display for the given value.
     *
     * @param value the value to derive the label from
     * @return the displayable label of the given value
     */
    protected abstract String createValueLabel(V value);

    @Override
    public Optional<?> computeValueUpdate(Map<String, String> parameterContext) {
        return updater.apply(parameterContext).map(values -> {
            ArrayNode result = Json.createArray();
            values.forEach(value -> result.add(Json.createObject()
                                                   .put("value", createValueName(value))
                                                   .put("text", createValueLabel(value))));
            return result;
        });
    }

    @Override
    protected String checkAndTransformValue(Value input) {
        List<?> rawValues;
        if (input.get() instanceof List<?> list) {
            rawValues = list;
        } else {
            rawValues = Arrays.asList(input.asString().split(Pattern.quote(DELIMITER)));
        }

        String verifiedInput = rawValues.stream()
                                        .map(Value::of)
                                        .map(this::checkAndTransformSingleValue)
                                        .filter(Objects::nonNull)
                                        .collect(Collectors.joining(DELIMITER));
        return Strings.isFilled(verifiedInput) ? verifiedInput : null;
    }

    /**
     * Checks and transforms a single selected value.
     *
     * @param input a single selected value wrapped as <tt>Value</tt>
     * @return the serialized string representation of the given value or <tt>null</tt> to skip the value.
     * Note that the representation must not contain the {@link #DELIMITER} as it is used to separate multiple values.
     * @throws sirius.kernel.health.HandledException in case of invalid data which should be reported to the user
     */
    protected abstract String checkAndTransformSingleValue(Value input);

    /**
     * {@inheritDoc}
     * <p>
     * Note that values which can no longer be resolved (e.g. deleted entities) are silently skipped here, as
     * this is also invoked when rendering the job form and therefore must not throw. Strict validation happens
     * via {@link #checkAndTransformSingleValue(Value)} whenever a job is submitted or started. Also note that
     * an empty optional (rather than an empty list) is returned if no value could be resolved at all, so that
     * {@link #require(Map)} and presence checks properly detect values which became invalid after submission.
     */
    @Override
    protected Optional<List<V>> resolveFromString(Value input) {
        return input.asOptionalString()
                    .map(encodedValues -> Stream.of(encodedValues.split(Pattern.quote(DELIMITER)))
                                                .filter(Strings::isFilled)
                                                .map(Value::of)
                                                .map(this::resolveSingleValueFromString)
                                                .flatMap(Optional::stream)
                                                .toList())
                    .filter(values -> !values.isEmpty());
    }

    /**
     * Resolves a single serialized value (as created by {@link #checkAndTransformSingleValue(Value)}) into the
     * actual parameter value.
     *
     * @param input the serialized string representation of a single value wrapped as <tt>Value</tt>
     * @return the resolved value wrapped as optional or an empty optional if the value couldn't be resolved
     */
    protected abstract Optional<V> resolveSingleValueFromString(Value input);

    /**
     * Describes a selectable option for this parameter including whether it is currently selected.
     *
     * @param name     the name of the option
     * @param label    the displayable name of the option
     * @param selected <tt>true</tt> if this option is currently selected, <tt>false</tt> otherwise
     */
    public record MultiSelectValue(String name, String label, boolean selected) {
    }
}
