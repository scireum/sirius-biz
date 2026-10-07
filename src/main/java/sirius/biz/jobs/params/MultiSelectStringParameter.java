package sirius.biz.jobs.params;

import sirius.kernel.commons.CachingSupplier;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.nls.NLS;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Provides a multi select parameter from a fixed list of key-value pairs.
 * <p>
 * Using {@link #allowCustomEntries()}, the user may additionally enter arbitrary values which are not part of the
 * list. The list may then also be left empty to provide a free text multi select.
 */
public class MultiSelectStringParameter extends MultiSelectParameter<String, MultiSelectStringParameter> {

    private final Map<String, String> entries = new LinkedHashMap<>();

    private Supplier<Map<String, String>> entriesProvider;
    private Supplier<List<String>> defaultValueProvider;
    private boolean allowCustomEntries = false;

    /**
     * Creates a new parameter with the given name and label.
     *
     * @param name  the name of the parameter
     * @param label the label of the parameter, which will be {@link NLS#smartGet(String) auto translated}
     */
    public MultiSelectStringParameter(String name, String label) {
        super(name, label);
    }

    /**
     * Adds an entry to the list.
     *
     * @param key   the entry key
     * @param value the display value, which will be {@link NLS#smartGet(String) auto translated before display}
     * @return the parameter itself for fluent method calls
     */
    public MultiSelectStringParameter withEntry(String key, String value) {
        if (this.entriesProvider != null) {
            throw new IllegalStateException("Entries can not be added when an entries provider is set.");
        }

        this.entries.put(key, value);
        return self();
    }

    /**
     * Sets a provider that generates the collection of selectable entries.
     * <p>
     * Use this to lazily initialize the entries. A {@link CachingSupplier} can be used to cache the entries.
     *
     * @param entriesProvider the provider that returns list of entries
     * @return the parameter itself for fluent method calls
     */
    public MultiSelectStringParameter withEntriesProvider(Supplier<Map<String, String>> entriesProvider) {
        if (!entries.isEmpty()) {
            throw new IllegalStateException("An entry provider can not be set after entries have already been added.");
        }

        this.entriesProvider = entriesProvider;
        return self();
    }

    /**
     * Sets a provider that generates the default value for this parameter.
     * <p>
     * A <tt>Supplier</tt> is used instead of a constant value to support dynamic default values.
     * A {@link CachingSupplier} can be used to cache the supplier result (the default values).
     *
     * @param defaultValueProvider a supplier which returns the default values to use
     * @return the parameter itself for fluent method calls
     */
    public MultiSelectStringParameter withDefaultProvider(Supplier<List<String>> defaultValueProvider) {
        this.defaultValueProvider = defaultValueProvider;
        return this;
    }

    /**
     * Permits the user to enter values which are not part of the list of entries.
     *
     * @return the parameter itself for fluent method calls
     */
    public MultiSelectStringParameter allowCustomEntries() {
        this.allowCustomEntries = true;
        return self();
    }

    @Override
    public boolean isAllowCustomEntries() {
        return allowCustomEntries;
    }

    private Map<String, String> fetchEntriesMap() {
        if (entriesProvider != null) {
            return entriesProvider.get();
        }
        return entries;
    }

    /**
     * Enumerates all values provided by the parameter.
     * <p>
     * If {@linkplain #allowCustomEntries() custom entries} are permitted, selected values which are not part of the
     * list of entries are appended, so that they are still displayed when the form is rendered again.
     *
     * @return list of {@link MultiSelectValue entries} with the key as name and display value as label
     */
    @Override
    public List<MultiSelectValue> getValues(Map<String, String> context) {
        String contextValue = context.get(getName());
        List<String> selectedValues =
                contextValue == null ? List.of() : Arrays.asList(contextValue.split(Pattern.quote(DELIMITER)));

        Map<String, String> entriesMap = fetchEntriesMap();
        List<MultiSelectValue> values = new ArrayList<>();
        entriesMap.forEach((key, label) -> values.add(new MultiSelectValue(key,
                                                                           NLS.smartGet(label),
                                                                           selectedValues.contains(key))));

        if (allowCustomEntries) {
            selectedValues.stream()
                          .filter(Strings::isFilled)
                          .filter(value -> !entriesMap.containsKey(value))
                          .distinct()
                          .map(value -> new MultiSelectValue(value, value, true))
                          .forEach(values::add);
        }

        return values;
    }

    @Override
    protected String createValueName(String value) {
        return value;
    }

    @Override
    protected String createValueLabel(String value) {
        return NLS.smartGet(fetchEntriesMap().getOrDefault(value, value));
    }

    @Override
    protected String checkAndTransformValue(Value input) {
        if (input.isNull() && defaultValueProvider != null) {
            return String.join(DELIMITER, defaultValueProvider.get());
        }

        return super.checkAndTransformValue(input);
    }

    @Override
    protected String checkAndTransformSingleValue(Value input) {
        String rawInput = input.asString().trim();

        // we can not allow the delimiter within values, as we obviously use it to separate values from each other
        if (rawInput.contains(DELIMITER)) {
            return null;
        }

        if (allowCustomEntries) {
            return Strings.isFilled(rawInput) ? rawInput : null;
        }

        if (!fetchEntriesMap().containsKey(rawInput)) {
            return null;
        }

        return rawInput;
    }

    @Override
    protected Optional<String> resolveSingleValueFromString(@Nonnull Value input) {
        return Optional.ofNullable(checkAndTransformSingleValue(input));
    }
}
