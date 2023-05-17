package sirius.biz.jobs.params;

import sirius.kernel.commons.CachingSupplier;
import sirius.kernel.commons.Json;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Value;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;

import javax.annotation.Nonnull;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Provides a single select parameter from a list of key-value pairs.
 */
public class SelectStringParameter extends SelectParameter<String, SelectStringParameter> {

    private final Map<String, String> entries = new LinkedHashMap<>();

    private Supplier<Map<String, String>> entriesProvider;

    /**
     * Creates a new parameter with the given name and label.
     *
     * @param name  the name of the parameter
     * @param label the label of the parameter, which will be {@link NLS#smartGet(String) auto translated}
     */
    public SelectStringParameter(String name, String label) {
        super(name, label);
    }

    /**
     * Adds an entry to the list.
     *
     * @param key   the entry key
     * @param value the display value, which will be {@link NLS#smartGet(String) auto translated before display}
     * @return the parameter itself for fluent method calls
     */
    public SelectStringParameter withEntry(String key, String value) {
        if (this.entriesProvider != null) {
            throw Exceptions.createHandled()
                            .withSystemErrorMessage("Entries can not be added when an entries provider is set.")
                            .handle();
        }

        this.entries.put(key, value);
        return self();
    }

    /**
     * Sets a provider that provides the entries.
     *
     * @param entriesProvider the provider that returns list of entries
     * @return the parameter itself for fluent method calls
     */
    public SelectStringParameter withEntriesProvider(Supplier<Map<String, String>> entriesProvider) {
        if (!entries.isEmpty()) {
            throw Exceptions.createHandled()
                            .withSystemErrorMessage(
                                    "An entry provider can not be set after entries have already been added.")
                            .handle();
        }

        if (!(entriesProvider instanceof CachingSupplier)) {
            entriesProvider = new CachingSupplier<>(entriesProvider);
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
        if (Strings.isEmpty(input) || !fetchEntriesMap().containsKey(input.asString())) {
            return null;
        }
        return input.asString();
    }

    @Override
    public Optional<?> computeValueUpdate(Map<String, String> parameterContext) {
        return updater.apply(parameterContext)
                      .map(value -> Json.createObject()
                                        .put("value", value)
                                        .put("text", NLS.smartGet(fetchEntriesMap().get(value))));
    }

    @Override
    protected Optional<String> resolveFromString(@Nonnull Value input) {
        return input.asOptionalString();
    }
}
