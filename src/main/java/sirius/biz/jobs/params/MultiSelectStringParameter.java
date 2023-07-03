package sirius.biz.jobs.params;

import sirius.kernel.commons.CachingSupplier;
import sirius.kernel.commons.Json;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Value;
import sirius.kernel.nls.NLS;

import javax.annotation.Nonnull;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Provides a multi select parameter from a list of key-value pairs.
 */
public class MultiSelectStringParameter extends MultiSelectParameter<String, MultiSelectStringParameter> {

    private final Map<String, String> entries = new LinkedHashMap<>();

    private Supplier<Map<String, String>> entriesProvider;

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

    private Map<String, String> fetchEntriesMap() {
        if (entriesProvider != null) {
            return entriesProvider.get();
        }
        return entries;
    }

    /**
     * Enumerates all values provided by the parameter.
     *
     * @return list of {@linkplain Tuple entries} with the key as first and display value as second tuple items.
     */
    @Override
    public List<Tuple<String, String>> getValues() {
        return fetchEntriesMap().keySet()
                                .stream()
                                .map(entry -> Tuple.create(entry, NLS.smartGet(fetchEntriesMap().get(entry))))
                                .toList();
    }

    @Override
    protected String checkAndTransformValue(Value input) {
        if (!(input.get() instanceof List<?> list)) {
            return checkAndTransformSingleValue(input);
        }

        String verifiedInput = list.stream()
                                   .map(Value::of)
                                   .map(this::checkAndTransformSingleValue)
                                   .filter(Objects::nonNull)
                                   .collect(Collectors.joining(","));
        return Strings.isFilled(verifiedInput) ? verifiedInput : null;
    }

    private String checkAndTransformSingleValue(Value input) {
        String rawInput = input.asString().trim();

        // we use the comma to separate the values, and thus can not allow it within values
        if (rawInput.contains(",")) {
            return null;
        }

        if (!fetchEntriesMap().containsKey(rawInput)) {
            return null;
        }

        return rawInput;
    }

    @Override
    protected Optional<List<String>> resolveFromString(@Nonnull Value input) {
        return input.asOptionalString()
                    .map(string -> Stream.of(string.split(","))
                                         .map(Value::of)
                                         .map(this::checkAndTransformSingleValue)
                                         .filter(Objects::nonNull)
                                         .collect(Collectors.toList()));
    }
}
