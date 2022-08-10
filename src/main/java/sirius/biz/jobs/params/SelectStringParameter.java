package sirius.biz.jobs.params;

import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Value;
import sirius.kernel.nls.NLS;

import javax.annotation.Nonnull;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Provides a single select parameter from a list of key-value pairs.
 */
public class SelectStringParameter extends SelectParameter<String, SelectStringParameter> {

    private final Map<String, String> entries = new LinkedHashMap<>();

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
        this.entries.put(key, value);
        return self();
    }

    /**
     * Enumerates all values provided by the parameter.
     *
     * @return list of {@link Tuple entries} with the key as first and display value as second tuple items.
     */
    @Override
    public List<Tuple<String, String>> getValues() {
        return entries.keySet()
                      .stream()
                      .map(entry -> Tuple.create(entry, NLS.smartGet(entries.get(entry))))
                      .collect(Collectors.toList());
    }

    @Override
    protected String checkAndTransformValue(Value input) {
        if (Strings.isEmpty(input) || !entries.containsKey(input.asString())) {
            return null;
        }
        return input.asString();
    }

    @Override
    public Optional<?> computeValueUpdate(Map<String, String> parameterContext) {
        return updater.apply(parameterContext)
                      .map(value -> Map.of("value", value, "text", NLS.smartGet(entries.get(value))));
    }

    @Override
    protected Optional<String> resolveFromString(@Nonnull Value input) {
        return input.asOptionalString();
    }
}
